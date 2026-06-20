package cc.nanoic.yunanexus.file.support;

import cc.nanoic.yunanexus.file.config.FileProperties;
import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class LocalStorageProvider implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    private final Path storageRoot;

    public LocalStorageProvider(FileProperties fileProperties) {
        this.storageRoot = Path.of(fileProperties.getStoragePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory: " + storageRoot, e);
        }
    }

    @Override
    public String store(String objectKey, InputStream inputStream, long contentLength) {
        try {
            Path target = resolvePath(objectKey);
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                inputStream.transferTo(out);
            }
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + objectKey, e);
        }
    }

    @Override
    public InputStream read(String objectKey) {
        try {
            return Files.newInputStream(resolvePath(objectKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + objectKey, e);
        }
    }

    @Override
    public boolean delete(String objectKey) {
        try {
            return Files.deleteIfExists(resolvePath(objectKey));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", objectKey, e);
            return false;
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolvePath(objectKey));
    }

    public String generateObjectKey(String fileExt) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return datePath + "/" + IdUtil.fastSimpleUUID() + (fileExt.isEmpty() ? "" : "." + fileExt);
    }

    private Path resolvePath(String objectKey) {
        return storageRoot.resolve(objectKey).normalize();
    }
}