package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.file.entity.DTO.QuotaResult;
import cc.nanoic.yunanexus.file.entity.FileStorageQuota;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.mapper.FileStorageQuotaMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class QuotaService {
    // TODO: 配额服务
    private final FileStorageQuotaMapper quotaMapper;
    private final UserFileMapper userFileMapper;

    public QuotaService(FileStorageQuotaMapper quotaMapper, UserFileMapper userFileMapper) {
        this.quotaMapper = quotaMapper;
        this.userFileMapper = userFileMapper;
    }

    public QuotaResult getQuota() {
        byte[] globalId = PermissionContext.getGlobalId();
        Set<String> roles = PermissionContext.getRoles();

        FileStorageQuota effective = resolveQuota(roles);
        long used = calcUsedStorage(globalId);

        QuotaResult quotaResult = new QuotaResult();
        quotaResult.setRoleName(effective.getRoleName());
        quotaResult.setSingleFileUnlimited(effective.getMaxSingleFileSize() == 0);
        quotaResult.setMaxSingleFileSize(effective.getMaxSingleFileSize());
        quotaResult.setTotalStorageUnlimited(effective.getMaxTotalStorage() == 0);
        quotaResult.setMaxTotalStorage(effective.getMaxTotalStorage());
        quotaResult.setUsedStorage(used);
        quotaResult.setRemainingStorage(
                quotaResult.isTotalStorageUnlimited() ? -1
                        : Math.max(0, effective.getMaxTotalStorage() - used));
        return quotaResult;
    }

    public void checkUploadSize(long fileSize) {
        QuotaResult quota = getQuota();

        if (!quota.isSingleFileUnlimited() && fileSize > quota.getMaxSingleFileSize()) {
            throw new RuntimeException("单文件大小超过角色[" + quota.getRoleName() + "]限制: 需要 " + formatSize(fileSize)
                    + ", 上限 " + formatSize(quota.getMaxSingleFileSize()));
        }

        if (!quota.isTotalStorageUnlimited() && fileSize > quota.getRemainingStorage()) {
            throw new RuntimeException("存储空间不足: 需要 " + formatSize(fileSize)
                    + ", 已用 " + formatSize(quota.getUsedStorage())
                    + "/" + formatSize(quota.getMaxTotalStorage()));
        }
    }

    private long calcUsedStorage(byte[] globalId) {

        return userFileMapper.selectObjs(
                new LambdaQueryWrapper<UserFile>()
                        .select(UserFile::getFileSize)
                        .eq(UserFile::getGlobalId, globalId)
                        .eq(UserFile::getDeleteStage, 0)
                        .eq(UserFile::getStatus, 1))
                .stream().mapToLong(obj -> obj == null ? 0L : (Long) obj)
                .sum();
    }

    private FileStorageQuota resolveQuota(Set<String> roles) {
        List<FileStorageQuota> all = quotaMapper.selectList(
                new LambdaQueryWrapper<FileStorageQuota>()
                        .eq(FileStorageQuota::getStatus, 1)
                        .orderByDesc(FileStorageQuota::getPriority));

        for (FileStorageQuota quota : all) {
            if (roles.contains(quota.getRoleName())) {
                return quota;
            }
        }

        FileStorageQuota fallback = new FileStorageQuota();
        fallback.setRoleName("USER");
        fallback.setMaxSingleFileSize(53687091200L);
        fallback.setMaxTotalStorage(214748364800L);
        return fallback;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        if (bytes < 1048576)
            return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1073741824)
            return String.format("%.1fMB", bytes / 1048576.0);
        return String.format("%.1fGB", bytes / 1073741824.0);
    }

}
