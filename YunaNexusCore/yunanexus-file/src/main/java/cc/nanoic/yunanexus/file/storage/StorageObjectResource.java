package cc.nanoic.yunanexus.file.storage;

import java.io.InputStream;

public record StorageObjectResource(
        InputStream inputStream,
        long contentLength,
        String contentType,
        String downloadName
) {
}