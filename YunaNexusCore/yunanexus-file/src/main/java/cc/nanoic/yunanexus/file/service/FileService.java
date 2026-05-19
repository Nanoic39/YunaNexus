package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.file.client.UserInternalClient;
import cc.nanoic.yunanexus.file.config.AvatarUploadProperties;
import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.FileStorageNode;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.entity.UserFolder;
import cc.nanoic.yunanexus.file.mapper.FileObjectMapper;
import cc.nanoic.yunanexus.file.mapper.FileStorageNodeMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import cc.nanoic.yunanexus.file.mapper.UserFolderMapper;
import cc.nanoic.yunanexus.file.storage.StorageDriver;
import cc.nanoic.yunanexus.file.storage.StorageDriverRegistry;
import cc.nanoic.yunanexus.file.storage.StorageObjectResource;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileService {
    private static final String DEFAULT_SERVICE_NAME = "main-site";
    private static final String AVATAR_SERVICE_NAME = "user-avatar";
    private static final String HASH_ALGO = "SHA256";

    @Resource
    private FileStorageNodeMapper fileStorageNodeMapper;

    @Resource
    private FileObjectMapper fileObjectMapper;

    @Resource
    private UserFileMapper userFileMapper;

    @Resource
    private UserFolderMapper userFolderMapper;

    @Resource
    private StorageDriverRegistry storageDriverRegistry;

    @Resource
    private UserInternalClient userInternalClient;

    @Resource
    private AvatarUploadProperties avatarUploadProperties;

    @Transactional
    public Map<String, Object> upload(
            Long userId,
            MultipartFile file,
            Long folderId,
            Integer fileCategory,
            Integer publicStatus,
            String serviceName,
            String oauthAppUuid) {
        return uploadInternal(userId, file, folderId, fileCategory, publicStatus, serviceName, oauthAppUuid, false);
    }

    @Transactional
    private Map<String, Object> uploadInternal(
            Long userId,
            MultipartFile file,
            Long folderId,
            Integer fileCategory,
            Integer publicStatus,
            String serviceName,
            String oauthAppUuid,
            boolean allowReservedServiceName) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(R.PARAM_ERROR, "上传文件不能为空");
        }

        validateFolder(userId, folderId);

        int safeFileCategory = normalizeFileCategory(fileCategory);
        int safePublicStatus = safeFileCategory == 0 ? 1 : (Objects.equals(publicStatus, 1) ? 1 : 0);
        String safeServiceName = StringUtils.hasText(serviceName) ? serviceName.trim() : DEFAULT_SERVICE_NAME;
        if (!allowReservedServiceName && AVATAR_SERVICE_NAME.equals(safeServiceName)) {
            throw new BusinessException(R.PARAM_ERROR, "用户头像请使用专用上传接口");
        }

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "unnamed";
        String ext = FileUtil.extName(originalFilename);
        String mime = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        long fileSize = file.getSize();
        String fileHash = calculateSha256(file);

        FileObject fileObject = findReusableObject(fileHash, fileSize);
        if (fileObject == null) {
            FileStorageNode node = selectWritableNode();
            StorageDriver storageDriver = storageDriverRegistry.requireDriver(node.getStorageVendor());

            String objectUuid = UUID.randomUUID().toString();
            String objectKey = buildObjectKey(node.getNodeCode(), objectUuid, ext);

            try {
                storageDriver.putObject(node, file, objectKey);
            } catch (IOException e) {
                throw new BusinessException(R.SERVER_ERROR, "文件写入存储节点失败");
            }

            fileObject = new FileObject();
            fileObject.setObjectUuid(objectUuid);
            fileObject.setObjectKey(objectKey);
            fileObject.setPrimaryNodeId(node.getId());
            fileObject.setStorageType(0);
            fileObject.setFileHash(fileHash);
            fileObject.setHashAlgo(HASH_ALGO);
            fileObject.setFileSize(fileSize);
            fileObject.setFileExt(ext);
            fileObject.setFileMime(mime);
            fileObject.setIsEncrypted(0);
            fileObject.setCompressionType(0);
            fileObject.setRefCount(1L);
            fileObject.setStatus(1);
            fileObjectMapper.insert(fileObject);
        } else {
            fileObject.setRefCount((fileObject.getRefCount() == null ? 0L : fileObject.getRefCount()) + 1);
            fileObjectMapper.updateById(fileObject);
        }

        String logicalFileUuid = UUID.randomUUID().toString();
        String logicalFileName = resolveLogicalFileName(originalFilename, safeServiceName, logicalFileUuid);

        UserFile userFile = new UserFile();
        userFile.setFileUuid(logicalFileUuid);
        userFile.setUserId(userId);
        userFile.setFolderId(folderId);
        userFile.setObjectId(fileObject.getId());
        userFile.setFileCategory(safeFileCategory);
        userFile.setPublicStatus(safePublicStatus);
        userFile.setServiceName(safeServiceName);
        userFile.setOauthAppUuid(StringUtils.hasText(oauthAppUuid) ? oauthAppUuid.trim() : null);
        userFile.setOriginName(originalFilename);
        userFile.setFileName(logicalFileName);
        userFile.setFileSize(fileSize);
        userFile.setFileExt(ext);
        userFile.setFileMime(mime);
        userFile.setFileHash(fileHash);
        userFile.setVersionNo(1);
        userFile.setStatus(1);
        userFile.setDeleteStage(0);
        userFileMapper.insert(userFile);

        return getStringObjectMap(userFile, fileObject);
    }

    private static Map<String, Object> getStringObjectMap(UserFile userFile, FileObject fileObject) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileUuid", userFile.getFileUuid());
        data.put("objectUuid", fileObject.getObjectUuid());
        data.put("fileName", userFile.getFileName());
        data.put("fileSize", userFile.getFileSize());
        data.put("folderId", userFile.getFolderId());
        data.put("fileCategory", userFile.getFileCategory());
        data.put("publicStatus", userFile.getPublicStatus());
        data.put("serviceName", userFile.getServiceName());
        return data;
    }

    public List<Map<String, Object>> listUserFiles(Long userId, Long folderId) {
        LambdaQueryWrapper<UserFile> wrapper = new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getStatus, 1)
                .eq(UserFile::getDeleteStage, 0)
                .ne(UserFile::getServiceName, AVATAR_SERVICE_NAME)
                .orderByDesc(UserFile::getCreateTime)
                .orderByDesc(UserFile::getId);

        if (folderId == null) {
            wrapper.isNull(UserFile::getFolderId);
        } else {
            wrapper.eq(UserFile::getFolderId, folderId);
        }

        return userFileMapper.selectList(wrapper).stream().map(item -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileUuid", item.getFileUuid());
            data.put("fileName", item.getFileName());
            data.put("originName", item.getOriginName());
            data.put("fileSize", item.getFileSize());
            data.put("fileExt", item.getFileExt());
            data.put("fileMime", item.getFileMime());
            data.put("fileCategory", item.getFileCategory());
            data.put("publicStatus", item.getPublicStatus());
            data.put("serviceName", item.getServiceName());
            data.put("folderId", item.getFolderId());
            data.put("createTime", item.getCreateTime());
            return data;
        }).toList();
    }

    public Map<String, Object> getFileDetail(Long userId, String fileUuid) {
        UserFile item = requireManagedUserFile(userId, fileUuid, 0);
        FileObject fileObject = fileObjectMapper.selectById(item.getObjectId());
        FileStorageNode node = fileObject == null ? null
                : fileStorageNodeMapper.selectById(fileObject.getPrimaryNodeId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileUuid", item.getFileUuid());
        data.put("fileName", item.getFileName());
        data.put("originName", item.getOriginName());
        data.put("fileSize", item.getFileSize());
        data.put("fileExt", item.getFileExt());
        data.put("fileMime", item.getFileMime());
        data.put("fileHash", item.getFileHash());
        data.put("fileCategory", item.getFileCategory());
        data.put("publicStatus", item.getPublicStatus());
        data.put("serviceName", item.getServiceName());
        data.put("folderId", item.getFolderId());
        data.put("createTime", item.getCreateTime());
        data.put("objectId", item.getObjectId());
        data.put("objectUuid", fileObject == null ? null : fileObject.getObjectUuid());
        data.put("storageType", fileObject == null ? null : fileObject.getStorageType());
        data.put("hashAlgo", fileObject == null ? null : fileObject.getHashAlgo());
        data.put("refCount", fileObject == null ? null : fileObject.getRefCount());
        data.put("objectStatus", fileObject == null ? null : fileObject.getStatus());
        data.put("primaryNodeId", fileObject == null ? null : fileObject.getPrimaryNodeId());
        data.put("primaryNodeCode", node == null ? null : node.getNodeCode());
        data.put("primaryNodeName", node == null ? null : node.getNodeName());
        return data;
    }

    public void moveToRecycle(Long userId, String fileUuid) {
        UserFile item = requireManagedUserFile(userId, fileUuid, 0);
        LocalDateTime now = LocalDateTime.now();
        item.setDeleteStage(1);
        item.setDeletedAt(now);
        item.setRecycleExpireAt(now.plusDays(30));
        item.setDeletedBy(userId);
        userFileMapper.updateById(item);
    }

    public void restore(Long userId, String fileUuid) {
        UserFile item = requireManagedUserFile(userId, fileUuid, 1);
        item.setDeleteStage(0);
        item.setDeletedAt(null);
        item.setRecycleExpireAt(null);
        item.setPreDeleteExpireAt(null);
        item.setDeletedBy(null);
        userFileMapper.updateById(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadAvatar(Long userId, MultipartFile file) {
        validateAvatarUpload(file);
        Map<String, Object> data = uploadInternal(userId, file, null, 0, 1, AVATAR_SERVICE_NAME, null, true);
        String avatarUuid = String.valueOf(data.get("fileUuid"));
        var result = userInternalClient.updateAvatar(userId, avatarUuid);
        if (result == null || result.getCode() != R.SUCCESS.getCode()) {
            throw new BusinessException(R.SERVER_ERROR, "头像上传成功，但同步用户头像失败");
        }
        String previousAvatarUuid = null;
        if (result.getData() != null && result.getData().get("previousAvatarUuid") != null) {
            previousAvatarUuid = String.valueOf(result.getData().get("previousAvatarUuid"));
        }
        recycleAvatarFile(userId, previousAvatarUuid, avatarUuid);
        data.put("avatarUuid", avatarUuid);
        data.put("previousAvatarUuid", previousAvatarUuid);
        data.put("avatarAccessUrl", "/file/avatar/" + avatarUuid);
        data.put("managedByFileSystem", false);
        return data;
    }

    public List<Map<String, Object>> listRecycleFiles(Long userId) {
        return userFileMapper.selectList(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getStatus, 1)
                .eq(UserFile::getDeleteStage, 1)
                .ne(UserFile::getServiceName, AVATAR_SERVICE_NAME)
                .orderByDesc(UserFile::getDeletedAt)
                .orderByDesc(UserFile::getId))
                .stream()
                .map(item -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("fileUuid", item.getFileUuid());
                    data.put("fileName", item.getFileName());
                    data.put("originName", item.getOriginName());
                    data.put("fileSize", item.getFileSize());
                    data.put("fileExt", item.getFileExt());
                    data.put("fileMime", item.getFileMime());
                    data.put("folderId", item.getFolderId());
                    data.put("deletedAt", item.getDeletedAt());
                    data.put("recycleExpireAt", item.getRecycleExpireAt());
                    return data;
                }).toList();
    }

    public StorageObjectResource getPublicFile(String fileUuid) {
        UserFile item = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getFileUuid, fileUuid)
                .eq(UserFile::getStatus, 1)
                .eq(UserFile::getDeleteStage, 0)
                .ne(UserFile::getServiceName, AVATAR_SERVICE_NAME)
                .and(w -> w.eq(UserFile::getPublicStatus, 1).or().eq(UserFile::getFileCategory, 0))
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException(R.NOT_FOUND, "公开文件不存在或不可访问");
        }
        FileObject fileObject = fileObjectMapper.selectById(item.getObjectId());
        FileStorageNode node = fileObject == null ? null
                : fileStorageNodeMapper.selectById(fileObject.getPrimaryNodeId());
        if (fileObject == null || node == null) {
            throw new BusinessException(R.NOT_FOUND, "文件对象不存在或存储节点不可用");
        }
        try {
            return storageDriverRegistry.requireDriver(node.getStorageVendor())
                    .getObject(node, fileObject.getObjectKey(), item.getFileMime(), item.getFileName());
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "读取公开文件失败");
        }
    }

    public StorageObjectResource getAvatarPublicFile(String avatarUuid) {
        UserFile item = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getFileUuid, avatarUuid)
                .eq(UserFile::getServiceName, AVATAR_SERVICE_NAME)
                .eq(UserFile::getStatus, 1)
                .eq(UserFile::getDeleteStage, 0)
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException(R.NOT_FOUND, "头像文件不存在或不可访问");
        }
        FileObject fileObject = fileObjectMapper.selectById(item.getObjectId());
        FileStorageNode node = fileObject == null ? null
                : fileStorageNodeMapper.selectById(fileObject.getPrimaryNodeId());
        if (fileObject == null || node == null) {
            throw new BusinessException(R.NOT_FOUND, "头像对象不存在或存储节点不可用");
        }
        try {
            return storageDriverRegistry.requireDriver(node.getStorageVendor())
                    .getObject(node, fileObject.getObjectKey(), item.getFileMime(), item.getFileName());
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "读取用户头像失败");
        }
    }

    private void recycleAvatarFile(Long userId, String previousAvatarUuid, String currentAvatarUuid) {
        if (!StringUtils.hasText(previousAvatarUuid) || Objects.equals(previousAvatarUuid, currentAvatarUuid)) {
            return;
        }
        UserFile previousAvatar = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getFileUuid, previousAvatarUuid)
                .eq(UserFile::getServiceName, AVATAR_SERVICE_NAME)
                .eq(UserFile::getStatus, 1)
                .eq(UserFile::getDeleteStage, 0)
                .last("limit 1"));
        if (previousAvatar == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        previousAvatar.setDeleteStage(2);
        previousAvatar.setDeletedAt(now);
        previousAvatar.setRecycleExpireAt(null);
        previousAvatar.setPreDeleteExpireAt(now.plusDays(60));
        previousAvatar.setDeletedBy(userId);
        userFileMapper.updateById(previousAvatar);
    }

    private void validateAvatarUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(R.PARAM_ERROR, "头像文件不能为空");
        }
        if (file.getSize() > avatarUploadProperties.getMaxSizeBytes()) {
            throw new BusinessException(R.PARAM_ERROR, "头像文件大小超过限制");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)
                || !avatarUploadProperties.getAllowedContentTypes().contains(contentType)) {
            throw new BusinessException(R.PARAM_ERROR, "头像文件类型不受支持");
        }
        String extension = FileUtil.extName(file.getOriginalFilename());
        if (!StringUtils.hasText(extension)
                || !avatarUploadProperties.getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new BusinessException(R.PARAM_ERROR, "头像文件扩展名不受支持");
        }
    }

    public List<Map<String, Object>> listUserFolders(Long userId, Long parentId) {
        LambdaQueryWrapper<UserFolder> wrapper = new LambdaQueryWrapper<UserFolder>()
                .eq(UserFolder::getUserId, userId)
                .eq(UserFolder::getStatus, 1)
                .eq(UserFolder::getDeleteStage, 0)
                .orderByAsc(UserFolder::getSortNo)
                .orderByAsc(UserFolder::getId);

        if (parentId == null) {
            wrapper.isNull(UserFolder::getParentId);
        } else {
            wrapper.eq(UserFolder::getParentId, parentId);
        }

        return userFolderMapper.selectList(wrapper).stream().map(item -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("folderUuid", item.getFolderUuid());
            data.put("folderName", item.getFolderName());
            data.put("folderType", item.getFolderType());
            data.put("serviceName", item.getServiceName());
            data.put("oauthAppUuid", item.getOauthAppUuid());
            data.put("folderPath", item.getFolderPath());
            data.put("depth", item.getDepth());
            data.put("parentId", item.getParentId());
            data.put("createTime", item.getCreateTime());
            return data;
        }).toList();
    }

    private UserFile requireManagedUserFile(Long userId, String fileUuid, int deleteStage) {
        UserFile item = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getFileUuid, fileUuid)
                .eq(UserFile::getStatus, 1)
                .eq(UserFile::getDeleteStage, deleteStage)
                .ne(UserFile::getServiceName, AVATAR_SERVICE_NAME)
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException(R.NOT_FOUND, "目标文件不存在或不可操作");
        }
        return item;
    }

    private void validateFolder(Long userId, Long folderId) {
        if (folderId == null) {
            return;
        }

        UserFolder folder = userFolderMapper.selectOne(new LambdaQueryWrapper<UserFolder>()
                .eq(UserFolder::getId, folderId)
                .eq(UserFolder::getUserId, userId)
                .eq(UserFolder::getStatus, 1)
                .eq(UserFolder::getDeleteStage, 0)
                .last("limit 1"));

        if (folder == null) {
            throw new BusinessException(R.NOT_FOUND, "目标目录不存在或不可用");
        }
    }

    private FileStorageNode selectWritableNode() {
        FileStorageNode node = fileStorageNodeMapper.selectOne(new LambdaQueryWrapper<FileStorageNode>()
                .eq(FileStorageNode::getStatus, 1)
                .eq(FileStorageNode::getHealthStatus, 1)
                .orderByDesc(FileStorageNode::getWeight)
                .orderByAsc(FileStorageNode::getId)
                .last("limit 1"));

        if (node == null) {
            throw new BusinessException(R.SERVER_ERROR, "暂无可用文件存储节点");
        }
        return node;
    }

    private FileObject findReusableObject(String fileHash, long fileSize) {
        return fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getFileHash, fileHash)
                .eq(FileObject::getFileSize, fileSize)
                .eq(FileObject::getHashAlgo, HASH_ALGO)
                .eq(FileObject::getStatus, 1)
                .last("limit 1"));
    }

    private String calculateSha256(MultipartFile file) {
        try {
            return DigestUtil.sha256Hex(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(R.SERVER_ERROR, "文件哈希计算失败");
        }
    }

    private String buildObjectKey(String nodeCode, String objectUuid, String ext) {
        LocalDate today = LocalDate.now();
        String suffix = StringUtils.hasText(ext) ? "." + ext : "";
        return String.format(
                "%s/%d/%02d/%02d/%s%s",
                nodeCode,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                objectUuid,
                suffix);
    }

    private String resolveLogicalFileName(String originalFilename, String serviceName, String logicalFileUuid) {
        if (!AVATAR_SERVICE_NAME.equals(serviceName)) {
            return originalFilename;
        }
        String ext = FileUtil.extName(originalFilename);
        return StringUtils.hasText(ext) ? "avatar-" + logicalFileUuid + "." + ext : "avatar-" + logicalFileUuid;
    }

    private int normalizeFileCategory(Integer fileCategory) {
        if (fileCategory == null || fileCategory < 0 || fileCategory > 2) {
            return 1;
        }
        return fileCategory;
    }
}