package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.file.entity.FileUploadChunk;
import cc.nanoic.yunanexus.file.entity.FileUploadTask;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.mapper.FileUploadChunkMapper;
import cc.nanoic.yunanexus.file.mapper.FileUploadTaskMapper;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChunkUploadService {

    private static final Logger log = LoggerFactory.getLogger(ChunkUploadService.class);
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;

    private final FileUploadTaskMapper taskMapper;
    private final FileUploadChunkMapper chunkMapper;
    private final FileService fileService;
    private final Path chunkTempDir;

    public ChunkUploadService(FileUploadTaskMapper taskMapper, FileUploadChunkMapper chunkMapper,
            FileService fileService) {
        this.taskMapper = taskMapper;
        this.chunkMapper = chunkMapper;
        this.fileService = fileService;
        this.chunkTempDir = Path.of("./chunks").toAbsolutePath();
        try {
            Files.createDirectories(chunkTempDir);
        } catch (IOException e) {
            throw new RuntimeException("创建临时目录块失败", e);
        }
    }

    @Transactional
    public Map<String, Object> init(byte[] globalId, Long folderId, String fileName, long fileSize, String fileExt,
            String fileMime, Integer fileCategory) {
        long chunkSize = DEFAULT_CHUNK_SIZE;
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        FileUploadTask task = new FileUploadTask();
        task.setUploadId(IdUtil.fastSimpleUUID());
        task.setGlobalId(globalId);
        task.setFolderId(folderId);
        task.setFileName(fileName);
        task.setFileSize(fileSize);
        task.setFileExt(fileExt);
        task.setFileMime(fileMime);
        task.setChunkSize(chunkSize);
        task.setTotalChunks(totalChunks);
        task.setUploadedChunks(0);
        task.setFileCategory(fileCategory != null ? fileCategory : 1);
        task.setPublicStatus(0);
        task.setServiceName("main-site");
        task.setStatus(0);
        taskMapper.insert(task);

        Path taskDir = chunkTempDir.resolve(task.getUploadId());
        try {
            Files.createDirectories(taskDir);
        } catch (IOException e) {
            throw new RuntimeException("创建块目录失败", e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("uploadId", task.getUploadId());
        result.put("chunkSize", chunkSize);
        result.put("totalChunks", totalChunks);
        return result;
    }

    @Transactional
    public void uploadChunk(String uploadId, int chunkIndex, byte[] data) {
        FileUploadTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<FileUploadTask>()
                        .eq(FileUploadTask::getUploadId, uploadId));
        if (task == null) {
            throw new RuntimeException("上传任务不存在");
        }
        if (task.getStatus() != 0) {
            throw new RuntimeException("上传任务已结束");
        }
        if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new RuntimeException("分片索引越界");
        }

        String etag = computeMd5(data);

        FileUploadChunk existing = chunkMapper.selectOne(
                new LambdaQueryWrapper<FileUploadChunk>()
                        .eq(FileUploadChunk::getUploadId, uploadId)
                        .eq(FileUploadChunk::getChunkIndex, chunkIndex));
        if (existing != null && etag.equals(existing.getEtag())) {
            return;
        }

        Path chunkFile = chunkTempDir.resolve(uploadId).resolve(String.valueOf(chunkIndex));
        try {
            Files.write(chunkFile, data);
        } catch (IOException e) {
            throw new RuntimeException("写入分片文件失败", e);
        }

        if (existing == null) {
            FileUploadChunk chunk = new FileUploadChunk();
            chunk.setUploadId(uploadId);
            chunk.setChunkIndex(chunkIndex);
            chunk.setChunkSize((long) data.length);
            chunk.setEtag(etag);
            chunk.setStatus(1);
            chunkMapper.insert(chunk);
            task.setUploadedChunks(task.getUploadedChunks() + 1);
            taskMapper.updateById(task);
        }
    }

    @Transactional
    public UserFile complete(String uploadId) {
        FileUploadTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<FileUploadTask>()
                        .eq(FileUploadTask::getUploadId, uploadId));
        if (task == null) {
            throw new RuntimeException("上传任务不存在");
        }
        if (task.getStatus() != 0) {
            throw new RuntimeException("上传任务已结束");
        }

        List<FileUploadChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<FileUploadChunk>()
                        .eq(FileUploadChunk::getUploadId, uploadId)
                        .orderByAsc(FileUploadChunk::getChunkIndex));

        if (chunks.size() != task.getTotalChunks()) {
            throw new RuntimeException("分片未全部上传: " + chunks.size() + "/" + task.getTotalChunks());
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Path taskDir = chunkTempDir.resolve(uploadId);
        try {
            for (int i = 0; i < task.getTotalChunks(); i++) {
                Path chunkFile = taskDir.resolve(String.valueOf(i));
                byte[] chunkData = Files.readAllBytes(chunkFile);
                bos.write(chunkData);
            }
        } catch (IOException e) {
            throw new RuntimeException("合并分片失败", e);
        }

        byte[] merged = bos.toByteArray();
        UserFile userFile = fileService.upload(task.getGlobalId(), task.getFolderId(),
                task.getFileName(), task.getFileMime(), merged, task.getFileCategory());

        task.setStatus(1);
        taskMapper.updateById(task);

        cleanupChunks(uploadId);

        return userFile;
    }

    public void abort(String uploadId, byte[] globalId) {
        FileUploadTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<FileUploadTask>()
                        .eq(FileUploadTask::getUploadId, uploadId)
                        .eq(FileUploadTask::getGlobalId, globalId));
        if (task == null) {
            throw new RuntimeException("上传任务不存在");
        }
        task.setStatus(2);
        taskMapper.updateById(task);
        cleanupChunks(uploadId);
    }

    private void cleanupChunks(String uploadId) {
        Path taskDir = chunkTempDir.resolve(uploadId);
        try {
            if (Files.exists(taskDir)) {
                try (var stream = Files.walk(taskDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.warn("清理数据块目录失败: {}", uploadId, e);
        }
    }

    private String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(data)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}