package cc.nanoic.yunanexus.file.scan;

import cc.nanoic.yunanexus.file.config.FileProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClamAV 扫描客户端（clamd INSTREAM 协议）。
 *
 * <p>选 INSTREAM 而不是调用 clamscan 命令行：命令行每次都要加载病毒库（几百 MB，数秒），
 * 而 clamd 常驻内存、单次扫描在毫秒级。上传路径上不能承受"每次加载病毒库"。
 *
 * @author yunanexus
 */
@Slf4j
public class ClamAvScanner implements VirusScanner {

    /** clamd 默认返回：无病毒 */
    private static final String OK = "stream: OK";

    private final String host;
    private final int port;
    private final int timeoutMillis;

    public ClamAvScanner(FileProperties.Scan config) {
        this.host = config.getClamdHost();
        this.port = config.getClamdPort();
        this.timeoutMillis = config.getTimeoutMillis();
    }

    @Override
    public ScanResult scan(Path path) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {
                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                byte[] buffer = new byte[8192];
                try (InputStream file = Files.newInputStream(path)) {
                    int read;
                    while ((read = file.read(buffer)) > 0) {
                        writeChunk(out, buffer, read);
                    }
                }
                writeChunk(out, new byte[0], 0);
                out.flush();
                String reply = readReply(in);
                if (reply.contains(OK)) {
                    return new ScanResult(true, reply, "clamav");
                }
                // 形如 stream: Win.Test.EICAR_HDB-1 FOUND
                return new ScanResult(false, reply, "clamav");
            }
        } catch (IOException error) {
            throw new ScanUnavailableException("clamd 不可达: " + error.getMessage(), error);
        }
    }

    @Override
    public boolean available() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {
                out.write("zPING\0".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String reply = readReply(in);
                return reply.contains("PONG");
            }
        } catch (IOException error) {
            log.debug("clamd 健康检查失败: {}", error.getMessage());
            return false;
        }
    }

    private void writeChunk(OutputStream out, byte[] data, int length) throws IOException {
        out.write(new byte[]{
                (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length
        });
        if (length > 0) {
            out.write(data, 0, length);
        }
    }

    private String readReply(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == 0) {
                break;
            }
            builder.append((char) ch);
        }
        return builder.toString().trim();
    }
}
