package cc.nanoic.yunanexus.file.support;

import java.io.InputStream;

public interface FileStorageProvider {

    String generateObjectKey(String fileExt);

    String store(String objectKey, InputStream inputStream, long contentLength);

    InputStream read(String objectKey);

    boolean delete(String objectKey);

    boolean exists(String objectKey);
}