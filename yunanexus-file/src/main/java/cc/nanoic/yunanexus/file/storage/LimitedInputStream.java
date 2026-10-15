package cc.nanoic.yunanexus.file.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 限定读取长度的输入流（HTTP Range 下载）。<p>
 *
 * 最多从底层流读取 {@code limit} 字节，读满即返回 {@code -1}（EOF），
 * 不关闭底层流以外的额外资源。关闭本流会一并关闭底层流。
 *
 * @author yunanexus
 */
final class LimitedInputStream extends FilterInputStream {

    private long remaining;

    LimitedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = Math.max(limit, 0);
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int b = in.read();
        if (b != -1) {
            remaining--;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int toRead = (int) Math.min(len, remaining);
        int read = in.read(b, off, toRead);
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        long toSkip = Math.min(n, remaining);
        long skipped = in.skip(toSkip);
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }
}
