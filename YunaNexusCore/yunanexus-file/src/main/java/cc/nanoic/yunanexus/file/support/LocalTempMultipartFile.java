package cc.nanoic.yunanexus.file.support;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalTempMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;

    public LocalTempMultipartFile(String name, String originalFilename, String contentType, Path path) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.path = path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        try {
            return getSize() <= 0;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
        Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}