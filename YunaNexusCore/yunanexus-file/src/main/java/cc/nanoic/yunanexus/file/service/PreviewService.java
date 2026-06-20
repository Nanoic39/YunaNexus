package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.FileShare;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.mapper.FileObjectMapper;
import cc.nanoic.yunanexus.file.mapper.FileShareTargetMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import cc.nanoic.yunanexus.file.support.FileStorageProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PreviewService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "xml", "yaml", "yml", "toml",
            "properties", "cfg", "ini", "conf", "log", "csv",
            "html", "htm", "css", "scss", "less",
            "js", "ts", "jsx", "tsx", "vue", "svelte",
            "py", "java", "go", "rs", "c", "cpp", "h", "hpp",
            "sql", "sh", "bat", "ps1", "env", "gitignore", "dockerfile");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "jar", "war", "ear");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "ogg", "mov", "avi", "mkv", "flv", "wmv");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus");
    private static final Map<String, String> MIME_MAP = Map.ofEntries(
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"),
            Map.entry("ogg", "video/ogg"), Map.entry("mov", "video/quicktime"),
            Map.entry("avi", "video/x-msvideo"), Map.entry("mkv", "video/x-matroska"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("flac", "audio/flac"), Map.entry("aac", "audio/aac"),
            Map.entry("opus", "audio/opus"), Map.entry("m4a", "audio/mp4"),
            Map.entry("wma", "audio/x-ms-wma"));

    private final FileObjectMapper fileObjectMapper;
    private final UserFileMapper userFileMapper;
    private final FileShareTargetMapper fileShareTargetMapper;
    private final FileStorageProvider storageProvider;

    public PreviewService(FileObjectMapper fileObjectMapper,
            UserFileMapper userFileMapper,
            FileShareTargetMapper fileShareTargetMapper,
            FileStorageProvider storageProvider) {
        this.fileObjectMapper = fileObjectMapper;
        this.userFileMapper = userFileMapper;
        this.fileShareTargetMapper = fileShareTargetMapper;
        this.storageProvider = storageProvider;
    }

    public PreviewResult getPreview(String fileUuid) {
        UserFile userFile = userFileMapper.selectOne(
                new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getFileUuid, fileUuid)
                        .eq(UserFile::getDeleteStage, 0));
        if (userFile == null)
            throw new RuntimeException("文件不存在");
        if (!canAccessPreview(userFile))
            throw new RuntimeException("无权预览此文件");
        FileObject fo = fileObjectMapper.selectById(userFile.getObjectId());
        if (fo == null)
            throw new RuntimeException("物理文件不存在");
        String ext = fo.getFileExt().toLowerCase();
        String mime = fo.getFileMime() != null ? fo.getFileMime() : "application/octet-stream";
        if (IMAGE_EXTENSIONS.contains(ext) || VIDEO_EXTENSIONS.contains(ext)
                || AUDIO_EXTENSIONS.contains(ext)) {
            return previewRaw(fo, MIME_MAP.getOrDefault(ext, mime));
        }
        if (TEXT_EXTENSIONS.contains(ext))
            return previewText(fo);
        if (ARCHIVE_EXTENSIONS.contains(ext))
            return previewArchive(fo);
        throw new RuntimeException("该文件类型不支持预览");
    }

    private boolean canAccessPreview(UserFile userFile) {
        byte[] currentUser = PermissionContext.getGlobalId();
        if (currentUser != null && Arrays.equals(currentUser, userFile.getGlobalId())) {
            return true;
        }
        List<FileShare> shares = fileShareTargetMapper.findActiveSharesWithPreview(userFile.getFileUuid());
        if (shares.isEmpty())
            return false;
        for (FileShare share : shares) {
            if (share.getNeedLogin() == 0)
                return true;
        }
        return currentUser != null;
    }

    private PreviewResult previewRaw(FileObject fo, String contentType) {
        try (InputStream is = storageProvider.read(fo.getObjectKey())) {
            return PreviewResult.binary(is.readAllBytes(), contentType);
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败", e);
        }
    }

    private PreviewResult previewText(FileObject fo) {
        try (InputStream is = storageProvider.read(fo.getObjectKey())) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return PreviewResult.text(text);
        } catch (Exception e) {
            try (InputStream is = storageProvider.read(fo.getObjectKey())) {
                String text = new String(is.readAllBytes(), StandardCharsets.ISO_8859_1);
                return PreviewResult.text(text);
            } catch (Exception ex) {
                throw new RuntimeException("文本预览失败", ex);
            }
        }
    }

    private PreviewResult previewArchive(FileObject fo) {
        try (InputStream is = storageProvider.read(fo.getObjectKey())) {
            StringBuilder sb = new StringBuilder("压缩包内容:\n" + "=".repeat(40) + "\n");
            try (ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String type = entry.isDirectory() ? "[目录]" : "[文件]";
                    String size = entry.isDirectory() ? "" : "  " + formatSize(entry.getSize());
                    sb.append(type).append("  ").append(entry.getName()).append(size).append("\n");
                }
            }
            sb.append("=".repeat(40)).append("\n");
            return PreviewResult.text(sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("压缩包预览失败", e);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        if (bytes < 1048576)
            return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1073741824)
            return String.format("%.1fMB", bytes / 1048576.0);
        return String.format("%.1fGB", bytes / 1073741824.0);
    }

    @Getter
    public static class PreviewResult {
        private final byte[] data;
        private final String contentType;

        private PreviewResult(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        public static PreviewResult binary(byte[] data, String contentType) {
            return new PreviewResult(data, contentType);
        }

        public static PreviewResult text(String content) {
            return new PreviewResult(content.getBytes(StandardCharsets.UTF_8), "text/plain;charset=UTF-8");
        }
    }
}