package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.file.config.FileUploadProperties;
import cc.nanoic.yunanexus.file.entity.FileUploadChunk;
import cc.nanoic.yunanexus.file.entity.FileUploadTask;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.mapper.FileUploadChunkMapper;
import cc.nanoic.yunanexus.file.mapper.FileUploadTaskMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import cc.nanoic.yunanexus.file.storage.StorageObjectResource;
import cc.nanoic.yunanexus.file.support.LocalTempMultipartFile;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileChunkService {
    @Resource
    private FileUploadProperties fileUploadProperties;

    @Resource
    private FileUploadTaskMapper fileUploadTaskMapper;

    @Resource
    private FileUploadChunkMapper fileUploadChunkMapper;

    @Resource
    private UserFileMapper userFileMapper;

    @Resource
    private FileService fileService;

    public Map<String, Object> initChunkUpload(Long userId, Map<String, Object> body) {
        String fileName = normalizeString(body.get("fileName"));
        String contentType = normalizeString(body.get("contentType"));
        Long fileSize = toLong(body.get("fileSize"));
        Long folderId = toLong(body.get("folderId"));

        if (!StringUtils.hasText(fileName) || fileSize == null || fileSize <= 0) {
            throw new BusinessException(R.PARAM_ERROR, "分片上传初始化参数不完整");
        }
        if (fileSize > fileUploadProperties.getNormalUserMaxFileSizeBytes()) {
            throw new BusinessException(R.PARAM_ERROR, "普通用户单文件最大支持 2GB");
        }
        ensureUserSpaceEnough(userId, fileSize);

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        long chunkSize = fileUploadProperties.getChunkSizeBytes();
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        FileUploadTask task = new FileUploadTask();
        task.setUploadId(uploadId);
        task.setUserId(userId);
        task.setFolderId(folderId);
        task.setFileName(fileName);
        task.setFileSize(fileSize);
        task.setFileExt(FileUtil.extName(fileName));
        task.setFileMime(StringUtils.hasText(contentType) ? contentType : "application/octet-stream");
        task.setChunkSize(chunkSize);
        task.setTotalChunks(totalChunks);
        task.setUploadedChunks(0);
        task.setFileCategory(1);
        task.setPublicStatus(0);
        task.setServiceName("main-site");
        task.setStatus(0);
        fileUploadTaskMapper.insert(task);

        createChunkDir(uploadId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("chunkSize", chunkSize);
        result.put("totalChunks", totalChunks);
        result.put("directUploadThresholdBytes", fileUploadProperties.getDirectUploadThresholdBytes());
        return result;
    }

    public Map<String, Object> uploadChunk(Long userId, String uploadId, Integer chunkIndex, MultipartFile file) {
        FileUploadTask task = requireTask(userId, uploadId);
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new BusinessException(R.PARAM_ERROR, "分片序号不合法");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(R.PARAM_ERROR, "分片文件不能为空");
        }
        if (file.getSize() > fileUploadProperties.getChunkSizeBytes()) {
            throw new BusinessException(R.PARAM_ERROR, "分片大小超过限制");
        }

        try {
            Path chunkPath = resolveChunkFile(uploadId, chunkIndex);
            Files.createDirectories(chunkPath.getParent());
            Files.copy(file.getInputStream(), chunkPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            saveChunkRecord(uploadId, chunkIndex, file);
            syncUploadedChunks(task);
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "分片写入失败");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("chunkIndex", chunkIndex);
        result.put("uploadedChunks", requireTask(userId, uploadId).getUploadedChunks());
        result.put("totalChunks", task.getTotalChunks());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completeChunkUpload(Long userId, String uploadId) {
        FileUploadTask task = requireTask(userId, uploadId);
        long uploadedCount = fileUploadChunkMapper.selectCount(
                new LambdaQueryWrapper<FileUploadChunk>().eq(FileUploadChunk::getUploadId, uploadId).eq(FileUploadChunk::getStatus, 1)
        );
        if (uploadedCount != task.getTotalChunks()) {
            throw new BusinessException(R.PARAM_ERROR, "仍有分片未上传完成");
        }

        Path mergedFile = null;
        try {
            mergedFile = mergeChunks(task);
            var multipartFile = new LocalTempMultipartFile(
                    "file",
                    task.getFileName(),
                    task.getFileMime(),
                    mergedFile
            );
            Map<String, Object> result = fileService.uploadByChunk(
                    userId,
                    multipartFile,
                    task.getFolderId(),
                    task.getFileCategory(),
                    task.getPublicStatus(),
                    task.getServiceName(),
                    task.getOauthAppUuid()
            );
            task.setStatus(2);
            fileUploadTaskMapper.updateById(task);
            safeCleanupUpload(uploadId, mergedFile);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "分片合并失败");
        }
    }

    public ResponseEntity<byte[]> downloadChunk(Long userId, String fileUuid, long start, long end) {
        if (start < 0 || end < start) {
            throw new BusinessException(R.PARAM_ERROR, "下载区间不合法");
        }

        StorageObjectResource object = fileService.downloadUserFile(userId, fileUuid);
        long contentLength = object.contentLength();
        long safeEnd = Math.min(end, contentLength - 1);
        int expectedLength = (int) (safeEnd - start + 1);

        try (InputStream inputStream = object.inputStream()) {
            skipFully(inputStream, start);
            byte[] bytes = inputStream.readNBytes(expectedLength);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (StringUtils.hasText(object.contentType())) {
                mediaType = MediaType.parseMediaType(object.contentType());
            }
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + (start + bytes.length - 1) + "/" + contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(object.downloadName()).build().toString())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "文件分段下载失败");
        }
    }

    private void ensureUserSpaceEnough(Long userId, long fileSize) {
        long usedSpace = userFileMapper.selectList(new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getUserId, userId)
                        .eq(UserFile::getStatus, 1)
                        .eq(UserFile::getDeleteStage, 0)
                        .ne(UserFile::getServiceName, "user-avatar"))
                .stream()
                .map(UserFile::getFileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        if (usedSpace + fileSize > fileUploadProperties.getNormalUserMaxSpaceBytes()) {
            throw new BusinessException(R.PARAM_ERROR, "当前账户剩余空间不足");
        }
    }

    private FileUploadTask requireTask(Long userId, String uploadId) {
        FileUploadTask task = fileUploadTaskMapper.selectOne(new LambdaQueryWrapper<FileUploadTask>()
                .eq(FileUploadTask::getUploadId, uploadId)
                .eq(FileUploadTask::getUserId, userId)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(R.NOT_FOUND, "上传任务不存在");
        }
        return task;
    }

    private void saveChunkRecord(String uploadId, Integer chunkIndex, MultipartFile file) {
        FileUploadChunk chunk = fileUploadChunkMapper.selectOne(new LambdaQueryWrapper<FileUploadChunk>()
                .eq(FileUploadChunk::getUploadId, uploadId)
                .eq(FileUploadChunk::getChunkIndex, chunkIndex)
                .last("limit 1"));
        try {
            String etag = DigestUtil.sha256Hex(file.getInputStream());
            if (chunk == null) {
                chunk = new FileUploadChunk();
                chunk.setUploadId(uploadId);
                chunk.setChunkIndex(chunkIndex);
                chunk.setChunkSize(file.getSize());
                chunk.setEtag(etag);
                chunk.setStatus(1);
                fileUploadChunkMapper.insert(chunk);
                return;
            }
            chunk.setChunkSize(file.getSize());
            chunk.setEtag(etag);
            chunk.setStatus(1);
            fileUploadChunkMapper.updateById(chunk);
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "分片校验失败");
        }
    }

    private void syncUploadedChunks(FileUploadTask task) {
        Long count = fileUploadChunkMapper.selectCount(new LambdaQueryWrapper<FileUploadChunk>()
                .eq(FileUploadChunk::getUploadId, task.getUploadId())
                .eq(FileUploadChunk::getStatus, 1));
        task.setUploadedChunks(count == null ? 0 : count.intValue());
        task.setStatus(task.getUploadedChunks() >= task.getTotalChunks() ? 1 : 0);
        fileUploadTaskMapper.updateById(task);
    }

    private Path mergeChunks(FileUploadTask task) throws IOException {
        Path uploadDir = resolveUploadDir(task.getUploadId());
        Path mergedDir = Paths.get(fileUploadProperties.getTempRootPath(), "merged");
        Files.createDirectories(mergedDir);
        Path mergedFile = mergedDir.resolve(task.getUploadId() + ".merge");
        try (OutputStream outputStream = Files.newOutputStream(
                mergedFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        )) {
            for (int i = 0; i < task.getTotalChunks(); i++) {
                Path chunkPath = uploadDir.resolve(i + ".part");
                if (!Files.exists(chunkPath)) {
                    throw new BusinessException(R.PARAM_ERROR, "分片缺失，无法合并");
                }
                Files.copy(chunkPath, outputStream);
            }
        }
        return mergedFile;
    }

    private void safeCleanupUpload(String uploadId, Path mergedFile) {
        try {
            if (mergedFile != null) {
                Files.deleteIfExists(mergedFile);
            }
            Path uploadDir = resolveUploadDir(uploadId);
            if (Files.exists(uploadDir)) {
                try (var walk = Files.walk(uploadDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void createChunkDir(String uploadId) {
        try {
            Files.createDirectories(resolveUploadDir(uploadId));
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "创建分片目录失败");
        }
    }

    private Path resolveUploadDir(String uploadId) {
        return Paths.get(fileUploadProperties.getTempRootPath(), "chunks", uploadId);
    }

    private Path resolveChunkFile(String uploadId, Integer chunkIndex) {
        return resolveUploadDir(uploadId).resolve(chunkIndex + ".part");
    }

    private void skipFully(InputStream inputStream, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0) {
                if (inputStream.read() == -1) {
                    throw new IOException("unexpected eof");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private String normalizeString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long toLong(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}