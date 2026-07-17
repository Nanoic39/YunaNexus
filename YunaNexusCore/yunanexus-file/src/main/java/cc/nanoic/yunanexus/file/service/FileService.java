package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.entity.UserFolder;
import cc.nanoic.yunanexus.file.entity.VO.UserFileVO;
import cc.nanoic.yunanexus.file.mapper.FileObjectMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import cc.nanoic.yunanexus.file.mapper.UserFolderMapper;
import cc.nanoic.yunanexus.file.support.FileStorageProvider;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final UserFileMapper userFileMapper;
    private final FileObjectMapper fileObjectMapper;
    private final UserFolderMapper userFolderMapper;
    private final FileStorageProvider storageProvider;
    private final QuotaService quotaService;

    public FileService(UserFileMapper userFileMapper,
            FileObjectMapper fileObjectMapper,
            UserFolderMapper userFolderMapper,
            FileStorageProvider storageProvider,
            QuotaService quotaService) {
        this.userFileMapper = userFileMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.userFolderMapper = userFolderMapper;
        this.storageProvider = storageProvider;
        this.quotaService = quotaService;
    }

    @Transactional
    public UserFile upload(byte[] globalId, Long folderId, String originName,
            String mimeType, byte[] data, Integer fileCategory) {
        long fileSize = data.length;
        quotaService.checkUploadSize(fileSize);

        String ext = extractExt(originName);
        String fileHash = computeSha256(data);
        String objectKey = storageProvider.generateObjectKey(ext);

        FileObject fileObject = resolveOrCreateObject(objectKey, fileHash, fileSize, ext, mimeType, data);

        UserFile userFile = new UserFile();
        userFile.setFileUuid(IdUtil.fastSimpleUUID());
        userFile.setGlobalId(globalId);
        userFile.setFolderId(folderId);
        userFile.setObjectId(fileObject.getId());
        userFile.setFileCategory(fileCategory != null ? fileCategory : 1);
        userFile.setPublicStatus(0);
        userFile.setServiceName("main-site");
        userFile.setOriginName(originName);
        userFile.setFileSize(fileSize);
        userFile.setFileExt(ext);
        userFile.setFileMime(mimeType);
        userFile.setFileHash(fileHash);
        userFile.setVersionNo(1);
        userFile.setStatus(1);
        userFile.setDeleteStage(0);

        userFile.setFileName(resolveUniqueFileName(globalId, folderId, originName));

        userFileMapper.insert(userFile);
        return userFile;
    }

    private FileObject resolveOrCreateObject(String objectKey, String fileHash,
            long fileSize, String ext, String mimeType, byte[] data) {
        FileObject existing = fileObjectMapper.selectOne(
                new LambdaQueryWrapper<FileObject>()
                        .eq(FileObject::getFileHash, fileHash)
                        .eq(FileObject::getFileSize, fileSize)
                        .eq(FileObject::getHashAlgo, "SHA256")
                        .eq(FileObject::getStatus, 1)
                        .last("LIMIT 1"));

        if (existing != null) {
            fileObjectMapper.incrementRefCount(existing.getId());
            return existing;
        }

        FileObject fileObject = new FileObject();
        fileObject.setObjectUuid(IdUtil.fastSimpleUUID());
        fileObject.setObjectKey(objectKey);
        fileObject.setStorageType(0);
        fileObject.setFileHash(fileHash);
        fileObject.setHashAlgo("SHA256");
        fileObject.setFileSize(fileSize);
        fileObject.setFileExt(ext);
        fileObject.setFileMime(mimeType);
        fileObject.setRefCount(1L);
        fileObject.setStatus(1);

        try {
            fileObjectMapper.insert(fileObject);
            storageProvider.store(objectKey, new ByteArrayInputStream(data), fileSize);
        } catch (DuplicateKeyException e) {
            FileObject retry = fileObjectMapper.selectOne(
                    new LambdaQueryWrapper<FileObject>()
                            .eq(FileObject::getFileHash, fileHash)
                            .eq(FileObject::getFileSize, fileSize)
                            .eq(FileObject::getHashAlgo, "SHA256")
                            .last("LIMIT 1"));
            fileObjectMapper.incrementRefCount(retry.getId());
            return retry;
        }

        return fileObject;
    }

    private String resolveUniqueFileName(byte[] globalId, Long folderId, String originName) {
        String baseName = originName;
        String ext = extractExt(originName);
        String nameWithoutExt = ext.isEmpty() ? baseName : baseName.substring(0, baseName.lastIndexOf('.'));

        boolean exists = userFileMapper.exists(
                new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getGlobalId, globalId)
                        .eq(UserFile::getFolderId, folderId)
                        .eq(UserFile::getFileName, originName)
                        .eq(UserFile::getDeleteStage, 0)
                        .eq(UserFile::getServiceName, "main-site"));

        if (!exists)
            return originName;

        for (int i = 1; i < 100; i++) {
            String candidate = ext.isEmpty()
                    ? nameWithoutExt + "(" + i + ")"
                    : nameWithoutExt + "(" + i + ")." + ext;

            boolean conflict = userFileMapper.exists(
                    new LambdaQueryWrapper<UserFile>()
                            .eq(UserFile::getGlobalId, globalId)
                            .eq(UserFile::getFolderId, folderId)
                            .eq(UserFile::getFileName, candidate)
                            .eq(UserFile::getDeleteStage, 0)
                            .eq(UserFile::getServiceName, "main-site"));

            if (!conflict)
                return candidate;
        }

        return nameWithoutExt + "(" + System.currentTimeMillis() + ")" + (ext.isEmpty() ? "" : "." + ext);
    }

    public UserFile getByFileUuid(String fileUuid) {
        return userFileMapper.selectOne(
                new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getFileUuid, fileUuid)
                        .eq(UserFile::getDeleteStage, 0));
    }

    public FileObject getFileObject(Long objectId) {
        return fileObjectMapper.selectById(objectId);
    }

    public Page<UserFileVO> listFiles(byte[] globalId, Long folderId, String keyword, int page, int size) {
        LambdaQueryWrapper<UserFile> wrapper = new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getGlobalId, globalId)
                .eq(UserFile::getDeleteStage, 0)
                .eq(UserFile::getStatus, 1)
                .ne(UserFile::getFileCategory, 3)
                .orderByDesc(UserFile::getCreateTime);

        if (folderId != null) {
            wrapper.eq(UserFile::getFolderId, folderId);
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(UserFile::getFileName, keyword)
                    .or()
                    .like(UserFile::getOriginName, keyword));
        }

        Page<UserFile> entityPage = userFileMapper.selectPage(Page.of(page, size), wrapper);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<UserFileVO> voList = entityPage.getRecords().stream().map(entity -> {
            UserFileVO vo = new UserFileVO();
            vo.setFileUuid(entity.getFileUuid());
            vo.setName(entity.getFileName() != null ? entity.getFileName() : entity.getOriginName());
            vo.setSize(entity.getFileSize());
            vo.setFileType(entity.getFileMime());
            vo.setFileExt(entity.getFileExt());
            vo.setFolderId(entity.getFolderId() != null ? String.valueOf(entity.getFolderId()) : null);
            vo.setCreatedAt(entity.getCreateTime() != null ? entity.getCreateTime().format(fmt) : null);
            vo.setUpdatedAt(entity.getUpdateTime() != null ? entity.getUpdateTime().format(fmt) : null);

            // fileCategory: 2 = 文件夹
            boolean isDir = entity.getFileCategory() != null && entity.getFileCategory() == 2;
            vo.setIsFolder(isDir);

            if (isDir) {
                Long childCount = userFileMapper.selectCount(new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getGlobalId, globalId)
                        .eq(UserFile::getFolderId, entity.getId())
                        .eq(UserFile::getDeleteStage, 0)
                        .eq(UserFile::getStatus, 1));
                vo.setChildCount(childCount.intValue());
            }

            return vo;
        }).collect(Collectors.toList());

        Page<UserFileVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Transactional
    public void softDelete(String fileUuid, byte[] deletedBy) {
        UserFile userFile = getByFileUuid(fileUuid);
        if (userFile == null) {
            throw new RuntimeException("文件不存在");
        }
        userFile.setDeleteStage(1);
        userFile.setDeletedAt(LocalDateTime.now());
        userFile.setRecycleExpireAt(LocalDateTime.now().plusDays(30));
        userFile.setDeletedBy(deletedBy);
        userFileMapper.updateById(userFile);
    }

    @Transactional
    public void rename(String fileUuid, String newName) {
        UserFile userFile = getByFileUuid(fileUuid);
        if (userFile == null) {
            throw new RuntimeException("文件不存在");
        }
        userFile.setFileName(newName);
        String newExt = extractExt(newName);
        if (!newExt.isEmpty()) {
            userFile.setFileExt(newExt);
        }
        userFileMapper.updateById(userFile);
    }

    @Transactional
    public void move(String fileUuid, Long targetFolderId) {
        if (targetFolderId != null) {
            UserFolder folder = userFolderMapper.selectById(targetFolderId);
            if (folder == null) {
                throw new RuntimeException("目标目录不存在");
            }
        }
        userFileMapper.update(null,
                new LambdaUpdateWrapper<UserFile>()
                        .eq(UserFile::getFileUuid, fileUuid)
                        .set(UserFile::getFolderId, targetFolderId));
    }

    public byte[] download(String fileUuid) {
        UserFile userFile = getByFileUuid(fileUuid);
        if (userFile == null) {
            throw new RuntimeException("文件不存在");
        }
        FileObject fileObject = fileObjectMapper.selectById(userFile.getObjectId());
        if (fileObject == null) {
            throw new RuntimeException("物理文件不存在");
        }
        try (InputStream is = storageProvider.read(fileObject.getObjectKey())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("下载失败", e);
        }
    }

    public FileObject getObjectByFileUuid(String fileUuid) {
        UserFile userFile = getByFileUuid(fileUuid);
        if (userFile == null)
            return null;
        return fileObjectMapper.selectById(userFile.getObjectId());
    }

    private String extractExt(String fileName) {
        if (fileName == null || !fileName.contains("."))
            return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(data)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA256 compute failed", e);
        }
    }
}