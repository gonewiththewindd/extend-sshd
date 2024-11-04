package org.apache.sshd.jp.stream;

import java.io.IOException;

public interface SinkBuffer {

    int buffer(byte[] buffer, int offset, int length) throws IOException;

}
