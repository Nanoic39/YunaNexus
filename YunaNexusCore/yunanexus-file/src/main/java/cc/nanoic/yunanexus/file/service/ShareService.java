package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.file.entity.FileShare;
import cc.nanoic.yunanexus.file.entity.FileShareTarget;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.entity.UserFolder;
import cc.nanoic.yunanexus.file.mapper.FileShareMapper;
import cc.nanoic.yunanexus.file.mapper.FileShareTargetMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import cc.nanoic.yunanexus.file.mapper.UserFolderMapper;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShareService {

    private final FileShareMapper fileShareMapper;
    private final FileShareTargetMapper fileShareTargetMapper;
    private final UserFileMapper userFileMapper;
    private final UserFolderMapper userFolderMapper;

    public ShareService(FileShareMapper fileShareMapper,
            FileShareTargetMapper fileShareTargetMapper,
            UserFileMapper userFileMapper,
            UserFolderMapper userFolderMapper) {
        this.fileShareMapper = fileShareMapper;
        this.fileShareTargetMapper = fileShareTargetMapper;
        this.userFileMapper = userFileMapper;
        this.userFolderMapper = userFolderMapper;
    }

    @Transactional
    public FileShare createShare(byte[] globalId, List<String> targetUuids, List<Integer> targetTypes,
            String extractCode, Integer needLogin, Integer allowPreview,
            Long maxViewCount, Long maxDownloadCount, LocalDateTime expireAt) {
        if (targetUuids == null || targetUuids.isEmpty()) {
            throw new RuntimeException("分享目标不能为空");
        }
        for (int i = 0; i < targetUuids.size(); i++) {
            String uuid = targetUuids.get(i);
            Integer type = targetTypes.get(i);
            if (type == 0) {
                if (!userFileMapper.exists(new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getFileUuid, uuid)
                        .eq(UserFile::getGlobalId, globalId)
                        .eq(UserFile::getDeleteStage, 0))) {
                    throw new RuntimeException("文件不存在或不属于你: " + uuid);
                }
            } else if (type == 1) {
                if (!userFolderMapper.exists(new LambdaQueryWrapper<UserFolder>()
                        .eq(UserFolder::getFolderUuid, uuid)
                        .eq(UserFolder::getGlobalId, globalId)
                        .eq(UserFolder::getDeleteStage, 0))) {
                    throw new RuntimeException("目录不存在或不属于你: " + uuid);
                }
            } else {
                throw new RuntimeException("未知目标类型: " + type);
            }
        }

        FileShare share = new FileShare();
        share.setShareUuid(IdUtil.fastSimpleUUID());
        share.setShareCode(IdUtil.fastUUID());
        share.setGlobalId(globalId);
        share.setExtractCode(extractCode);
        share.setNeedLogin(needLogin != null ? needLogin : 1);
        share.setAllowPreview(allowPreview != null ? allowPreview : 0);
        share.setMaxViewCount(maxViewCount != null ? maxViewCount : 0L);
        share.setMaxDownloadCount(maxDownloadCount != null ? maxDownloadCount : 0L);
        share.setDownloadCount(0L);
        share.setViewCount(0L);
        share.setStatus(1);
        share.setExpireAt(expireAt);
        fileShareMapper.insert(share);

        for (int i = 0; i < targetUuids.size(); i++) {
            FileShareTarget target = new FileShareTarget();
            target.setShareId(share.getId());
            target.setTargetType(targetTypes.get(i));
            target.setTargetUuid(targetUuids.get(i));
            target.setSortNo(i);
            fileShareTargetMapper.insert(target);
        }
        return share;
    }

    public FileShare getByShareCode(String shareCode) {
        FileShare share = fileShareMapper.selectOne(
                new LambdaQueryWrapper<FileShare>().eq(FileShare::getShareCode, shareCode));
        if (share == null)
            throw new RuntimeException("分享不存在或已失效");
        if (share.getStatus() == 0)
            throw new RuntimeException("分享已被取消");
        if (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now())) {
            share.setStatus(2);
            fileShareMapper.updateById(share);
            throw new RuntimeException("分享已过期");
        }
        return share;
    }

    public List<FileShareTarget> getShareTargets(Long shareId) {
        return fileShareTargetMapper.selectList(
                new LambdaQueryWrapper<FileShareTarget>()
                        .eq(FileShareTarget::getShareId, shareId)
                        .orderByAsc(FileShareTarget::getSortNo));
    }

    @Transactional
    public FileShare verifyExtractCode(String shareCode, String extractCode) {
        FileShare share = getByShareCode(shareCode);
        if (share.getExtractCode() != null && !share.getExtractCode().isEmpty()
                && !share.getExtractCode().equals(extractCode)) {
            throw new RuntimeException("提取码错误");
        }
        if (share.getMaxViewCount() > 0 && share.getViewCount() >= share.getMaxViewCount()) {
            share.setStatus(0);
            fileShareMapper.updateById(share);
            throw new RuntimeException("分享已达到最大访问次数，已自动取消");
        }
        share.setViewCount(share.getViewCount() + 1);
        share.setLastViewedAt(LocalDateTime.now());
        if (share.getMaxViewCount() > 0 && share.getViewCount() >= share.getMaxViewCount()) {
            share.setStatus(0);
        }
        fileShareMapper.updateById(share);
        return share;
    }

    @Transactional
    public FileShare recordDownload(String shareCode) {
        FileShare share = getByShareCode(shareCode);
        if (share.getMaxDownloadCount() > 0
                && share.getDownloadCount() >= share.getMaxDownloadCount()) {
            share.setStatus(0);
            fileShareMapper.updateById(share);
            throw new RuntimeException("分享已达到最大下载次数，已自动取消");
        }
        share.setDownloadCount(share.getDownloadCount() + 1);
        share.setLastDownloadedAt(LocalDateTime.now());
        if (share.getMaxDownloadCount() > 0
                && share.getDownloadCount() >= share.getMaxDownloadCount()) {
            share.setStatus(0);
        }
        fileShareMapper.updateById(share);
        return share;
    }

    public Page<FileShare> listMyShares(byte[] globalId, int page, int size) {
        return fileShareMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<FileShare>()
                        .eq(FileShare::getGlobalId, globalId)
                        .orderByDesc(FileShare::getCreateTime));
    }

    @Transactional
    public void cancelShare(String shareCode, byte[] globalId) {
        FileShare share = fileShareMapper.selectOne(
                new LambdaQueryWrapper<FileShare>()
                        .eq(FileShare::getShareCode, shareCode)
                        .eq(FileShare::getGlobalId, globalId));
        if (share == null)
            throw new RuntimeException("分享不存在");
        share.setStatus(0);
        fileShareMapper.updateById(share);
    }
}