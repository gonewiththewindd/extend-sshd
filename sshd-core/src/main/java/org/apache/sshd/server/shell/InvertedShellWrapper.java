/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.server.shell;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.util.ExceptionUtils;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.logging.AbstractLoggingBean;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A shell implementation that wraps an instance of {@link InvertedShell} as a {@link Command}. This is useful when
 * using external processes. When starting the shell, this wrapper will also create a thread used to pump the streams
 * and also to check if the shell is alive.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class InvertedShellWrapper extends AbstractLoggingBean implements Command, ServerSessionAware {

    private final InvertedShell shell;
    private final Executor executor;
    private int bufferSize;
    private Duration pumpSleepTime;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private OutputStream shellIn;
    private InputStream shellOut;
    private InputStream shellErr;
    private ExitCallback callback;
    private boolean shutdownExecutor;

    private ScheduledExecutorService scheduleExecutors = Executors.newSingleThreadScheduledExecutor();

    /**
     * Auto-allocates an {@link Executor} in order to create the streams pump thread and uses the default
     * {@link CoreModuleProperties#BUFFER_SIZE}
     *
     * @param shell The {@link InvertedShell}
     * @see #InvertedShellWrapper(InvertedShell, int)
     */
    public InvertedShellWrapper(InvertedShell shell) {
        this(shell, CoreModuleProperties.BUFFER_SIZE.getRequiredDefault());
    }

    /**
     * Auto-allocates an {@link Executor} in order to create the streams pump thread
     *
     * @param shell      The {@link InvertedShell}
     * @param bufferSize Buffer size to use - must be above min. size ({@link Byte#SIZE})
     * @see #InvertedShellWrapper(InvertedShell, Executor, boolean, int)
     */
    public InvertedShellWrapper(InvertedShell shell, int bufferSize) {
        this(shell, null, true, bufferSize);
    }

    /**
     * @param shell            The {@link InvertedShell}
     * @param executor         The {@link Executor} to use in order to create the streams pump thread. If {@code null}
     *                         one is auto-allocated and shutdown when wrapper is {@code destroy()}-ed.
     * @param shutdownExecutor If {@code true} the executor is shut down when shell wrapper is {@code destroy()}-ed.
     *                         Ignored if executor service auto-allocated
     * @param bufferSize       Buffer size to use - must be above min. size ({@link Byte#SIZE})
     */
    public InvertedShellWrapper(InvertedShell shell, Executor executor, boolean shutdownExecutor, int bufferSize) {
        this.shell = Objects.requireNonNull(shell, "No shell");
        this.executor = (executor == null)
                ? ThreadUtils.newSingleThreadExecutor("shell[0x" + Integer.toHexString(shell.hashCode()) + "]") : executor;
        ValidateUtils.checkTrue(bufferSize > Byte.SIZE, "Copy buffer size too small: %d", bufferSize);
        this.bufferSize = bufferSize;
        this.pumpSleepTime = CoreModuleProperties.PUMP_SLEEP_TIME.getRequiredDefault();
        this.shutdownExecutor = (executor == null) || shutdownExecutor;

        this.commandMap.put("p", aaa -> AssetService.listAssets());
        this.commandMap.put("q", this::exit);
        this.commandMap.put("exit", this::exit);
    }

    private String exit(Object o) {
        try {
            byte[] exit = "exit".getBytes(StandardCharsets.UTF_8);
            shellIn.write(exit);
            shell.getSession().close(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void setSession(ServerSession session) {
        bufferSize = CoreModuleProperties.BUFFER_SIZE.getRequired(session);
        pumpSleepTime = CoreModuleProperties.PUMP_SLEEP_TIME.getRequired(session);
        ValidateUtils.checkTrue(GenericUtils.isPositive(pumpSleepTime),
                "Invalid " + CoreModuleProperties.PUMP_SLEEP_TIME + ": %d", pumpSleepTime);
        shell.setSession(session);
    }

    @Override
    public synchronized void start(ChannelSession channel, Environment env) throws IOException {
        // TODO propagate the Environment itself and support signal sending.
        shell.start(channel, env);
        shellIn = shell.getInputStream();
        shellOut = shell.getOutputStream();
        shellErr = shell.getErrorStream();

        logShellInfo(3000);
        sendAssets();

        executor.execute(this::pumpStreamsBlock);
//        scheduleExecutors.scheduleAtFixedRate(this::keepalive, 1, 30, TimeUnit.MILLISECONDS);
    }

    public void keepalive() {
        // session channel keep alive
        Buffer buffer = this.shell.getSession().createBuffer(SshConstants.SSH_MSG_IGNORE);
        try {
            if (shell.isAlive()) {
                this.shell.getSession().writePacket(buffer);
            } else {
                log.info("shell is not alive");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String PWD = "\r\n[Host]>";

    private void sendAssets() throws IOException {
        String assets = AssetService.listAssets();
        byte[] bytes = assets.getBytes(StandardCharsets.UTF_8);
        // 发送用户资产列表
        out.write(bytes);
        out.flush();
    }

    private void logShellInfo(long timeout) throws IOException {
        long start = System.currentTimeMillis();
        for (; ; ) {
            if (timeout < 0) {
                out.write("timeout".getBytes(StandardCharsets.UTF_8));
                return;
            }
            // ignore read out and error
            int shellOutAvailable = shellOut.available();
            if (shellOutAvailable > 0) {
                byte[] outBuffer = new byte[shellOutAvailable];
                shellOut.read(outBuffer, 0, shellOutAvailable);
                break;
            }
            int shellErrorAvailable = shellErr.available();
            if (shellErrorAvailable > 0) {
                byte[] errorBuffer = new byte[shellErrorAvailable];
                shellErr.read(errorBuffer, 0, shellErrorAvailable);
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            timeout -= System.currentTimeMillis() - start;
        }
    }

    @Override
    public synchronized void destroy(ChannelSession channel) throws Exception {
        Throwable err = null;
        try {
            shell.destroy(channel);
        } catch (Throwable e) {
            warn("destroy({}) failed ({}) to destroy shell: {}",
                    this, e.getClass().getSimpleName(), e.getMessage(), e);
            err = ExceptionUtils.accumulateException(err, e);
        }

        if (shutdownExecutor && (executor instanceof ExecutorService)) {
            try {
                ((ExecutorService) executor).shutdown();
            } catch (Exception e) {
                warn("destroy({}) failed ({}) to shut down executor: {}",
                        this, e.getClass().getSimpleName(), e.getMessage(), e);
                err = ExceptionUtils.accumulateException(err, e);
            }
        }

        if (err != null) {
            if (err instanceof Exception) {
                throw (Exception) err;
            } else {
                throw new RuntimeSshException(err);
            }
        }
    }

    private boolean localMode = true;
    public static final char ENTER_CHAR = 13;
    public final Map<String, Function<Object, String>> commandMap = new HashMap<>();

    protected void pumpStreamsBlock() {
        try {
            // Use a single thread to correctly sequence the output and error streams.
            // If any bytes are available from the output stream, send them first, then
            // check the error stream, or wait until more data is available.
            // TODO 部分结果转移 期望是获取所有结果
            // 指令执行与回显
            List<String> currentCommandBuffer = new ArrayList<>();
            for (; ; ) {
                // 读取输入
                if (localMode) {
                    processLocal(currentCommandBuffer);
                } else {
                    processRemote();
                }

                /*
                 * Make sure we exhausted all data - the shell might be dead but some data may still be in transit via
                 * pumping
                 */
                if ((!shell.isAlive()) && (in.available() <= 0) && (shellOut.available() <= 0) && (shellErr.available() <= 0)) {
                    callback.onExit(shell.exitValue());
                    return;
                }
                // Sleep a bit. This is not very good, as it consumes CPU, but the
                // input streams are not selectable for nio, and any other blocking
                // method would consume at least two threads
                Thread.sleep(pumpSleepTime.toMillis());
            }
        } catch (
                Throwable e) {
            boolean debugEnabled = log.isDebugEnabled();
            try {
                shell.destroy(shell.getServerChannelSession());
            } catch (Throwable err) {
                warn("pumpStreams({}) failed ({}) to destroy shell: {}",
                        this, e.getClass().getSimpleName(), e.getMessage(), e);
            }

            int exitValue = shell.exitValue();
            if (debugEnabled) {
                log.debug(
                        e.getClass().getSimpleName() + " while pumping the streams (exit=" + exitValue + "): " + e.getMessage(),
                        e);
            }
            callback.onExit(exitValue, e.getClass().getSimpleName());
        }
    }

    private void processRemote() {

    }

    private InputStream remoteOut;
    private OutputStream remoteIn;

    private void processLocal(List<String> currentCommandBuffer) throws IOException {
        int clientInAvailable = in.available();
        if (clientInAvailable <= 0) {
            return;
        }
        byte[] buffer = new byte[clientInAvailable];
        int len = in.read(buffer, 0, clientInAvailable);
        if (len < 0) {
            return;
        }
        String command = "";
        int i = 0;
        // 换行符解析
        String c = new String(buffer, 0, len, StandardCharsets.UTF_8);
        for (; i < c.length(); i++) {
            if (c.charAt(i) == ENTER_CHAR) {
                String trim = c.substring(0, i).trim();
                if (!Objects.equals(trim, "")) {
                    currentCommandBuffer.add(trim);
                }
                command = currentCommandBuffer.stream().collect(Collectors.joining(""));
                log.info("command:{}", command);
                currentCommandBuffer.clear();
                String rest = c.substring(i + 1, c.length());
                if (rest.length() > 0) {
                    currentCommandBuffer.add(rest);
                }
                break;
            }
        }
        if (!Objects.equals("", command)) {
            writeAndFlush(out, "\r\n".getBytes(StandardCharsets.UTF_8));
            // 指令解析:
            Function<Object, String> function = commandMap.get(command);
            if (Objects.nonNull(function)) {
                String result = function.apply(command);
                if (Objects.nonNull(result)) {
                    result += PWD;
                    writeAndFlush(out, result.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            String assetId = command;
            // 资产解析:
            Asset asset = AssetService.lookupAsset(assetId);
            if (Objects.isNull(asset)) {
                // 资产不存在
                String resp = "没有资产" + PWD;
                writeAndFlush(out, resp.getBytes(StandardCharsets.UTF_8));
            } else {
                // 登录远程
                SshClient client = SshClient.setUpDefaultClient();
                client.start();
                // using the client for multiple sessions...
                try (ClientSession session = client.connect(asset.getUsername(), asset.getAddress(), asset.getPort())
                        .verify(10, TimeUnit.SECONDS).getSession()) {
                    session.addPasswordIdentity(asset.getPassword()); // for password-based authentication
//                        session.addPublicKeyIdentity(...key - pair...); // for password-less authentication
                    // Note: can add BOTH password AND public key identities - depends on the client/server security setup
                    session.auth().verify(10, TimeUnit.SECONDS);
                    // start using the session to run commands, do SCP/SFTP, create local/remote port forwarding, etc...

                    try (ChannelShell shellChannel = session.createShellChannel()) {
                        shellChannel.setRedirectErrorStream(true);
                        shellChannel.open().verify(10000, TimeUnit.MILLISECONDS);

                        remoteIn = shellChannel.getInvertedIn();
                        remoteOut = shellChannel.getInvertedOut();
                        // 流转移
                        Thread thread = new Thread(() -> {
                            for (byte[] transfer = new byte[8192]; ; ) {
                                try {
                                    if (shellChannel.isClosed()) {
                                        return;
                                    }
                                    if (pumpStream(in, remoteIn, transfer)) {
                                        continue;
                                    }
                                    if (pumpStream(remoteOut, out, transfer)) {
                                        continue;
                                    }
                                    Thread.sleep(10);
                                } catch (IOException | InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                        thread.start();
                        // Wait (forever) for the channel to close - signalling shell exited
                        shellChannel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
                    }
                    String assets = AssetService.listAssets();
                    this.out.write(assets.concat(PWD).getBytes(StandardCharsets.UTF_8));
                    this.out.flush();
                }
            }
        } else {
            if (c.length() > 0 && c.charAt(0) != ENTER_CHAR) {
                currentCommandBuffer.add(c);
            }
            if (c.length() == 1 && c.charAt(0) == ENTER_CHAR) {
                c = c.concat(PWD);
            }
            out.write(c.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private void writeAndFlush(OutputStream out, byte[] buffer) throws IOException {
        out.write(buffer);
        out.flush();
    }

    private String extractStreamWithTimeout(InputStream in, int timeout) throws IOException {
        long start = System.currentTimeMillis();
        for (; ; ) {
            if (timeout < 0) {
                log.info("timeout...");
                return null;
            }
            int available = in.available();
            if (available > 0) {
                byte[] buffer = new byte[available];
                in.read(buffer);
                String result = new String(buffer, StandardCharsets.UTF_8);
                log.info("extract result:{}", result);
                return result;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            timeout -= (System.currentTimeMillis() - start);
            start = System.currentTimeMillis();
        }
    }

    private int pumpShellOut() throws IOException {
        int shellOutAvailable = shellOut.available();
        if (shellOutAvailable > 0) {
            byte[] outBuffer = new byte[shellOutAvailable];
            shellOut.read(outBuffer, 0, shellOutAvailable);
            String result = new String(outBuffer, "GBK");
            log.info("shell result(out):{}", result);
            byte[] convertResult = result.getBytes(StandardCharsets.UTF_8);
            out.write(convertResult);
            out.flush();
        } else if (shellOutAvailable == -1) {
            shellOut.close();
        }
        return shellOutAvailable;
    }

    private int pumpShellErr() throws IOException {
        int shellErrorAvailable = shellErr.available();
        if (shellErrorAvailable > 0) {
            byte[] errorBuffer = new byte[shellErrorAvailable];
            shellErr.read(errorBuffer, 0, shellErrorAvailable);
            String result = new String(errorBuffer, "GBK");
            log.info("shell result(error):{}", result);
            byte[] convertResult = result.getBytes(StandardCharsets.UTF_8);
            out.write(convertResult);
            out.flush();
        } else if (shellErrorAvailable == -1) {
            shellErr.close();
        }
        return shellErrorAvailable;
    }

    protected void pumpStreams() {
        try {
            // Use a single thread to correctly sequence the output and error streams.
            // If any bytes are available from the output stream, send them first, then
            // check the error stream, or wait until more data is available.
            // TODO 部分结果转移 期望是获取所有结果
            // 指令执行与回显
            for (byte[] buffer = new byte[bufferSize]; ; ) {
                if (pumpStream(in, shellIn, buffer)) {
                    continue;
                }
                if (pumpShellOutStream(shellOut, out)) {
                    continue;
                }
                if (pumpShellErrorStream(shellErr, err)) {
                    continue;
                }

                /*
                 * Make sure we exhausted all data - the shell might be dead but some data may still be in transit via
                 * pumping
                 */
                if ((!shell.isAlive()) && (in.available() <= 0) && (shellOut.available() <= 0) && (shellErr.available() <= 0)) {
                    callback.onExit(shell.exitValue());
                    return;
                }

                // Sleep a bit. This is not very good, as it consumes CPU, but the
                // input streams are not selectable for nio, and any other blocking
                // method would consume at least two threads
                Thread.sleep(pumpSleepTime.toMillis());
            }
        } catch (Throwable e) {
            boolean debugEnabled = log.isDebugEnabled();
            try {
                shell.destroy(shell.getServerChannelSession());
            } catch (Throwable err) {
                warn("pumpStreams({}) failed ({}) to destroy shell: {}",
                        this, e.getClass().getSimpleName(), e.getMessage(), e);
            }

            int exitValue = shell.exitValue();
            if (debugEnabled) {
                log.debug(
                        e.getClass().getSimpleName() + " while pumping the streams (exit=" + exitValue + "): " + e.getMessage(),
                        e);
            }
            callback.onExit(exitValue, e.getClass().getSimpleName());
        }
    }

    protected boolean pumpStream(InputStream in, OutputStream out, byte[] buffer) throws IOException {
        int available = in.available();

        if (available > 0) {
            int len = in.read(buffer);
            if (len > 0) {
                out.write(buffer, 0, len);
                out.flush();
                return true;
            }
        } else if (available == -1) {
            out.close();
        }
        return false;
    }

    protected boolean pumpShellOutStream(InputStream in, OutputStream out) throws IOException {
        int available = in.available();
        if (available > 0) {
            byte[] buffer = new byte[available];
            int len = in.read(buffer);
            if (len > 0) {
                // 转码
                String result = new String(buffer, "GBK");
                log.info("result(out):{}", result);
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
                out.flush();
                return true;
            }
        } else if (available == -1) {
            out.close();
        }
        return false;
    }

    protected boolean pumpShellErrorStream(InputStream in, OutputStream out) throws IOException {
        int available = in.available();
        if (available > 0) {
            byte[] buffer = new byte[available];
            int len = in.read(buffer);
            if (len > 0) {
                // 转码
                String result = new String(buffer, "GBK");
                log.info("result(error):{}", result);
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
                out.flush();
                return true;
            }
        } else if (available == -1) {
            out.close();
        }
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + shell;
    }
}
