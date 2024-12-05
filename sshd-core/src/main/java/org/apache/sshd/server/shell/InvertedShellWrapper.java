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
import org.apache.sshd.server.shell.jump.MainLoopService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

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
    private InputStream clientIn;
    private OutputStream clientOut;
    private OutputStream clientError;
    private OutputStream shellIn;
    private InputStream shellOut;
    private InputStream shellErr;
    private ExitCallback callback;
    private boolean shutdownExecutor;

    private MainLoopService mainLoopService;

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
        this.executor = (executor == null) ? ThreadUtils.newSingleThreadExecutor("shell[0x" + Integer.toHexString(shell.hashCode()) + "]") : executor;
        ValidateUtils.checkTrue(bufferSize > Byte.SIZE, "Copy buffer size too small: %d", bufferSize);
        this.bufferSize = bufferSize;
        this.pumpSleepTime = CoreModuleProperties.PUMP_SLEEP_TIME.getRequiredDefault();
        this.shutdownExecutor = (executor == null) || shutdownExecutor;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.clientIn = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.clientOut = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.clientError = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void setSession(ServerSession session) {
        bufferSize = CoreModuleProperties.BUFFER_SIZE.getRequired(session);
        pumpSleepTime = CoreModuleProperties.PUMP_SLEEP_TIME.getRequired(session);
        ValidateUtils.checkTrue(GenericUtils.isPositive(pumpSleepTime), "Invalid " + CoreModuleProperties.PUMP_SLEEP_TIME + ": %d", pumpSleepTime);
        shell.setSession(session);
    }

    @Override
    public synchronized void start(ChannelSession channel, Environment env) throws IOException {
        // TODO propagate the Environment itself and support signal sending.
        shell.start(channel, env);
        shellIn = shell.getInputStream();
        shellOut = shell.getOutputStream();
        shellErr = shell.getErrorStream();

        this.mainLoopService= new MainLoopService(shell, clientIn, clientOut, env);
        mainLoopService.onStartup();
        mainLoopService.debug();

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

    @Override
    public synchronized void destroy(ChannelSession channel) throws Exception {
        Throwable err = null;
        try {
            shell.destroy(channel);
        } catch (Throwable e) {
            warn("destroy({}) failed ({}) to destroy shell: {}", this, e.getClass().getSimpleName(), e.getMessage(), e);
            err = ExceptionUtils.accumulateException(err, e);
        }

        if (shutdownExecutor && (executor instanceof ExecutorService)) {
            try {
                ((ExecutorService) executor).shutdown();
            } catch (Exception e) {
                warn("destroy({}) failed ({}) to shut down executor: {}", this, e.getClass().getSimpleName(), e.getMessage(), e);
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

    protected void pumpStreamsBlock() {
        try {
            // Use a single thread to correctly sequence the output and error streams.
            // If any bytes are available from the output stream, send them first, then
            // check the error stream, or wait until more data is available.
            // TODO 部分结果转移 期望是获取所有结果
            // 指令执行与回显
            for (; ; ) {
                // 读取输入
                this.mainLoopService.loop();
                /*
                 * Make sure we exhausted all data - the shell might be dead but some data may still be in transit via
                 * pumping
                 */
                /*if (*//*(!shell.isAlive()) &&*//* (clientIn.available() <= 0) *//*&& (shellOut.available() <= 0) && (shellErr.available() <= 0)*//*) {
                    callback.onExit(shell.exitValue());
                    return;
                }*/
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
                warn("pumpStreams({}) failed ({}) to destroy shell: {}", this, e.getClass().getSimpleName(), e.getMessage(), e);
            }

            int exitValue = shell.exitValue();
            if (debugEnabled) {
                log.debug(e.getClass().getSimpleName() + " while pumping the streams (exit=" + exitValue + "): " + e.getMessage(), e);
            }
            callback.onExit(exitValue, e.getClass().getSimpleName());
            log.error(e.getMessage(), e);
        }
    }

    protected void pumpStreams() {
        try {
            // Use a single thread to correctly sequence the output and error streams.
            // If any bytes are available from the output stream, send them first, then
            // check the error stream, or wait until more data is available.
            // TODO 部分结果转移 期望是获取所有结果
            // 指令执行与回显
            for (byte[] buffer = new byte[bufferSize]; ; ) {
                if (pumpStream(clientIn, shellIn, buffer)) {
                    continue;
                }
                if (pumpShellOutStream(shellOut, clientOut)) {
                    continue;
                }
                if (pumpShellErrorStream(shellErr, clientError)) {
                    continue;
                }

                /*
                 * Make sure we exhausted all data - the shell might be dead but some data may still be in transit via
                 * pumping
                 */
                if ((!shell.isAlive()) && (clientIn.available() <= 0) && (shellOut.available() <= 0) && (shellErr.available() <= 0)) {
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
                warn("pumpStreams({}) failed ({}) to destroy shell: {}", this, e.getClass().getSimpleName(), e.getMessage(), e);
            }

            int exitValue = shell.exitValue();
            if (debugEnabled) {
                log.debug(e.getClass().getSimpleName() + " while pumping the streams (exit=" + exitValue + "): " + e.getMessage(), e);
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
