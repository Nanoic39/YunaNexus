package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileProperties;
import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import cc.nanoic.yunanexus.file.entity.FileQuota;
import cc.nanoic.yunanexus.file.mapper.FileQuotaMapper;
import cc.nanoic.yunanexus.file.shard.ShardRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 存储配额服务（A1）。
 *
 * <p>三个设计要点：
 * <ul>
 *   <li><b>配额行按 userId 分片</b>：文件本体按 fileId 分片，配额按用户分片，
 *       这样同一用户的用量永远只落在一个库里，查询与增减都是单库操作。</li>
 *   <li><b>在分片初始化阶段就拒绝</b>：让用户在传第一个字节之前就知道超限，
 *       而不是白传几个 G 之后在合并时失败。</li>
 *   <li><b>增减走 SQL 原子操作</b>：并发上传下"读-改-写"会丢计数，少算即是漏洞。</li>
 * </ul>
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class FileQuotaService {

    private final FileQuotaMapper quotaMapper;
    private final ShardRouter shardRouter;
    private final FileProperties properties;

    public FileQuotaService(FileQuotaMapper quotaMapper, ShardRouter shardRouter, FileProperties properties) {
        this.quotaMapper = quotaMapper;
        this.shardRouter = shardRouter;
        this.properties = properties;
    }

    /**
     * 校验是否还能容纳一次写入。
     *
     * @param userId       用户 ID
     * @param incomingSize 本次写入字节数
     * @throws BizException 超限时抛出 {@link ErrorCode#FILE_QUOTA_EXCEEDED}
     */
    public void ensure(long userId, long incomingSize) {
        FileQuota quota = load(userId);
        // 优先级：用户行显式覆盖 > 角色配额（取角色中最高者）> 全局默认
        long limitBytes = resolve(quota.getLimitBytes(), resolveRoleLimit(userId, true));
        long limitFiles = resolve(quota.getLimitFiles(), resolveRoleLimit(userId, false));
        if (quota.getUsedBytes() + incomingSize > limitBytes) {
            throw new BizException(ErrorCode.FILE_QUOTA_EXCEEDED,
                    "容量不足：已用 " + human(quota.getUsedBytes()) + "，本次需要 " + human(incomingSize)
                            + "，上限 " + human(limitBytes));
        }
        if (quota.getFileCount() + 1 > limitFiles) {
            throw new BizException(ErrorCode.FILE_QUOTA_EXCEEDED,
                    "文件数已达上限：" + quota.getFileCount() + "/" + limitFiles);
        }
    }

    /**
     * 记录一次成功写入。
     *
     * @param userId 用户 ID
     * @param bytes  字节数
     */
    public void addUsage(long userId, long bytes) {
        int shard = shardRouter.route(userId);
        ShardContextHolder.set(shard);
        try {
            quotaMapper.increment(userId, bytes, 1);
        } finally {
            ShardContextHolder.clear();
        }
    }

    /**
     * 记录一次删除。
     *
     * @param userId 用户 ID
     * @param bytes  字节数
     */
    public void removeUsage(long userId, long bytes) {
        int shard = shardRouter.route(userId);
        ShardContextHolder.set(shard);
        try {
            quotaMapper.decrement(userId, bytes, 1);
        } finally {
            ShardContextHolder.clear();
        }
    }

    /**
     * 查询用户当前用量。
     *
     * @param userId 用户 ID
     * @return 配额快照
     */
    public FileQuota usage(long userId) {
        return load(userId);
    }

    /**
     * 校正单个用户的用量（对账任务使用）。
     *
     * @param userId    用户 ID
     * @param bytes     真实用量
     * @param fileCount 真实文件数
     */
    public void correct(long userId, long bytes, long fileCount) {
        int shard = shardRouter.route(userId);
        ShardContextHolder.set(shard);
        try {
            quotaMapper.correct(userId, bytes, fileCount);
        } finally {
            ShardContextHolder.clear();
        }
    }

    /** 读配额；不存在时返回零值对象（不写库，避免读路径产生写放大） */
    private FileQuota load(long userId) {
        int shard = shardRouter.route(userId);
        ShardContextHolder.set(shard);
        try {
            FileQuota quota = quotaMapper.selectById(userId);
            if (quota == null) {
                FileQuota empty = new FileQuota();
                empty.setUserId(userId);
                empty.setUsedBytes(0L);
                empty.setFileCount(0L);
                empty.setLimitBytes(0L);
                empty.setLimitFiles(0L);
                return empty;
            }
            if (quota.getUsedBytes() == null) {
                quota.setUsedBytes(0L);
            }
            if (quota.getFileCount() == null) {
                quota.setFileCount(0L);
            }
            return quota;
        } finally {
            ShardContextHolder.clear();
        }
    }

    /**
     * 按角色解析配额上限。
     *
     * <p>角色来自网关注入的 {@code X-User-Roles}（权限字段）。用户可能有多个角色，
     * 取其中**最高**的配额——按"最小权限"给配额会让多角色用户反而受限，不符合直觉。
     *
     * @param userId 用户 ID
     * @param bytes  true 取容量上限，false 取文件数上限
     * @return 解析到的上限；无角色配额配置时返回全局默认
     */
    private long resolveRoleLimit(long userId, boolean bytes) {
        long fallback = bytes
                ? properties.getQuota().getDefaultLimitBytes()
                : properties.getQuota().getDefaultLimitFiles();
        FileProperties.RoleLimit[] table = properties.getQuota().getRoleLimits();
        if (table == null || table.length == 0) {
            return fallback;
        }
        java.util.List<String> roles = cc.nanoic.yunanexus.file.config.FileAccessContext.currentRoles();
        if (roles.isEmpty()) {
            return fallback;
        }
        long best = 0L;
        for (FileProperties.RoleLimit rule : table) {
            if (rule.getRole() == null || !roles.contains(rule.getRole())) {
                continue;
            }
            long candidate = bytes ? rule.getLimitBytes() : rule.getLimitFiles();
            if (candidate > 0) {
                best = Math.max(best, candidate);
            }
        }
        return best > 0 ? best : fallback;
    }

    /** 0 表示"未单独设置"，回落到全局默认 */
    private long resolve(Long override, long fallback) {
        return (override == null || override <= 0) ? fallback : override;
    }

    private String human(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.2f GiB", bytes / 1024.0 / 1024 / 1024);
        }
        if (bytes >= 1024L * 1024) {
            return String.format("%.2f MiB", bytes / 1024.0 / 1024);
        }
        if (bytes >= 1024L) {
            return String.format("%.2f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }
}
