package cc.nanoic.yunanexus.file.storage;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 本地磁盘文件存储（roadmap 7.2）。<p>
 *
 * 按日期分目录（{@code yyyy/MM/dd/{uuid}.{ext}}）存放，写入同时计算字节数与 SHA-256。
 * 路径访问前统一做根目录越界校验，防止目录穿越。<br>
 * 分片上传的临时分片 / 会话元数据落在 {@code chunks/{uploadId}/} 下（{@code {index}.part} /
 * {@code session.json}），合并完成后清理临时目录。
 *
 * @author yunanexus
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 分片上传临时根目录（相对存储根） */
    private static final String CHUNK_ROOT = "chunks";

    /** 分片文件后缀 */
    private static final String CHUNK_SUFFIX = ".part";

    /** 会话元数据文件名 */
    private static final String SESSION_FILE = "session.json";

    private final Path baseDir;

    public LocalFileStorage(FileProperties properties) {
        this.baseDir = Paths.get(properties.getStorage().getBaseDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("创建本地存储目录失败: " + baseDir, e);
        }
        log.info("本地文件存储根目录: {}", baseDir);
    }

    @Override
    public int storageType() {
        return 1;
    }

    @Override
    public StoredObject store(InputStream in, String ext) {
        String relative = newObjectPath(ext);
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream dis = new DigestInputStream(new BufferedInputStream(in), digest);
                 OutputStream out = Files.newOutputStream(target)) {
                size = dis.transferTo(out);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            return new StoredObject(relative, size, sha256);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "写入本地存储失败: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "SHA-256 不可用");
        }
    }

    @Override
    public InputStream open(String storagePath) {
        Path target = resolve(storagePath);
        if (!Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND, "存储文件缺失: " + storagePath);
        }
        try {
            return new BufferedInputStream(Files.newInputStream(target));
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED, "读取本地文件失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream open(String storagePath, long offset, long length) {
        Path target = resolve(storagePath);
        if (!Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND, "存储文件缺失: " + storagePath);
        }
        try {
            FileChannel channel = FileChannel.open(target, StandardOpenOption.READ);
            channel.position(offset);
            InputStream raw = Channels.newInputStream(channel);
            return length < 0 ? raw : new LimitedInputStream(raw, length);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED, "读取本地文件区间失败: " + e.getMessage());
        }
    }

    @Override
    public boolean exists(String storagePath) {
        return Files.isRegularFile(resolve(storagePath));
    }

    @Override
    public boolean delete(String storagePath) {
        try {
            return Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            log.warn("删除本地文件失败: {} ({})", storagePath, e.getMessage());
            return false;
        }
    }

    @Override
    public void storeChunk(String uploadId, int index, InputStream in) {
        Path dir = chunkDir(uploadId);
        Path part = dir.resolve(index + CHUNK_SUFFIX);
        try {
            Files.createDirectories(dir);
            Files.copy(in, part, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "写入分片失败: " + e.getMessage());
        }
    }

    @Override
    public Set<Integer> listChunks(String uploadId) {
        Path dir = chunkDir(uploadId);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(CHUNK_SUFFIX))
                    .map(name -> name.substring(0, name.length() - CHUNK_SUFFIX.length()))
                    .filter(this::isNumeric)
                    .map(Integer::parseInt)
                    .collect(TreeSet::new, Set::add, Set::addAll);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "读取分片列表失败: " + e.getMessage());
        }
    }

    @Override
    public void storeChunkSession(String uploadId, byte[] session) {
        Path dir = chunkDir(uploadId);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(SESSION_FILE), session);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "保存上传会话失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] loadChunkSession(String uploadId) {
        Path file = chunkDir(uploadId).resolve(SESSION_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_SESSION_NOT_FOUND, "读取上传会话失败: " + e.getMessage());
        }
    }

    @Override
    public StoredObject mergeChunks(String uploadId, int totalChunks, String ext) {
        Path dir = chunkDir(uploadId);
        if (!Files.isDirectory(dir)) {
            throw new BizException(ErrorCode.FILE_UPLOAD_SESSION_NOT_FOUND, "上传会话不存在: " + uploadId);
        }
        String relative = newObjectPath(ext);
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (OutputStream fileOut = Files.newOutputStream(target);
                 BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
                 DigestOutputStream digestOut = new DigestOutputStream(buffered, digest)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path part = dir.resolve(i + CHUNK_SUFFIX);
                    if (!Files.isRegularFile(part)) {
                        throw new BizException(ErrorCode.FILE_CHUNK_MISSING, "缺少分片 " + i);
                    }
                    try (InputStream in = Files.newInputStream(part)) {
                        size += in.transferTo(digestOut);
                    }
                }
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            deleteRecursively(dir);
            return new StoredObject(relative, size, sha256);
        } catch (BizException e) {
            cleanupQuietly(target);
            throw e;
        } catch (IOException e) {
            cleanupQuietly(target);
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "合并分片失败: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            cleanupQuietly(target);
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "SHA-256 不可用");
        }
    }

    @Override
    public List<String> listChunkUploads() {
        Path root = resolve(CHUNK_ROOT);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "读取上传会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public void abortChunks(String uploadId) {
        deleteRecursively(chunkDir(uploadId));
    }

    /** 生成新对象的相对存储路径（日期目录 + UUID + 扩展名）。 */
    private String newObjectPath(String ext) {
        return LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID()
                + (ext == null || ext.isBlank() ? "" : "." + ext);
    }

    /** 上传会话的临时目录（做根目录越界校验）。 */
    private Path chunkDir(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new BizException(ErrorCode.FILE_UPLOAD_SESSION_NOT_FOUND, "缺少上传会话 ID");
        }
        return resolve(CHUNK_ROOT + "/" + uploadId);
    }

    /** 是否为纯数字（分片下标）。 */
    private boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 递归删除目录及其内容（不存在则忽略）。 */
    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 单个临时文件删除失败不阻断整体流程
                }
            });
        } catch (IOException e) {
            log.warn("清理临时目录失败: {} ({})", dir, e.getMessage());
        }
    }

    /** 静默删除文件（用于合并失败时清理半成品）。 */
    private void cleanupQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 忽略
        }
    }

    /** 解析相对路径为绝对路径，并校验未越出根目录。 */
    private Path resolve(String relative) {
        Path resolved = baseDir.resolve(relative).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BizException(ErrorCode.FILE_ACCESS_DENIED, "非法存储路径: " + relative);
        }
        return resolved;
    }
}
