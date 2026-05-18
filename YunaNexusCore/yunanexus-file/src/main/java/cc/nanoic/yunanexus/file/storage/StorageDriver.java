package cc.nanoic.yunanexus.file.storage;

import cc.nanoic.yunanexus.file.entity.FileStorageNode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageDriver {
    boolean supports(Integer storageVendor);

    void putObject(FileStorageNode node, MultipartFile file, String objectKey) throws IOException;

    StorageObjectResource getObject(
            FileStorageNode node,
            String objectKey,
            String contentType,
            String downloadName
    ) throws IOException;
}