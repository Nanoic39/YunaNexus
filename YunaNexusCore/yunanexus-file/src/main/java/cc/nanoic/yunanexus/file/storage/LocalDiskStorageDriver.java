package cc.nanoic.yunanexus.file.storage;

import cc.nanoic.yunanexus.file.config.FileStorageProperties;
import cc.nanoic.yunanexus.file.entity.FileStorageNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class LocalDiskStorageDriver implements StorageDriver {
    private static final int STORAGE_VENDOR_LOCAL = 0;

    @Resource
    private FileStorageProperties fileStorageProperties;

    @Override
    public boolean supports(Integer storageVendor) {
        return storageVendor != null && storageVendor == STORAGE_VENDOR_LOCAL;
    }

    @Override
    public void putObject(FileStorageNode node, MultipartFile file, String objectKey) throws IOException {
        Path target = resolveTargetPath(objectKey);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public StorageObjectResource getObject(
            FileStorageNode node,
            String objectKey,
            String contentType,
            String downloadName) throws IOException {
        Path target = resolveTargetPath(objectKey);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IOException("目标文件不存在");
        }
        return new StorageObjectResource(
                Files.newInputStream(target),
                Files.size(target),
                contentType,
                downloadName);
    }

    private Path resolveTargetPath(String objectKey) throws IOException {
        String rootPath = fileStorageProperties.getRootPath();
        if (!StringUtils.hasText(rootPath)) {
            throw new IOException("未配置本地文件存储根目录");
        }

        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey.replace("/", java.io.File.separator)).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法文件路径");
        }
        return target;
    }
}