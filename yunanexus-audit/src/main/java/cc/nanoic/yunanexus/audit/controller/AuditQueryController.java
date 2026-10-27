package cc.nanoic.yunanexus.audit.controller;

import cc.nanoic.yunanexus.audit.entity.AuditLog;
import cc.nanoic.yunanexus.audit.service.AuditService;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口（roadmap 8.3）。<p>
 *
 * 供管理后台 / 运维排查使用：支持按用户、来源服务与操作时间范围过滤，
 * 指定 {@code userId} 时命中单一分片，否则跨全部分片归并后分页返回。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {

    @Resource
    private AuditService auditService;

    /**
     * 分页查询审计日志（按操作时间倒序）。
     *
     * @param userId    操作者用户 ID，缺省不过滤（跨分片）
     * @param service   来源服务，缺省不过滤
     * @param startTime 操作时间下界（epoch 毫秒，含），缺省不限
     * @param endTime   操作时间上界（epoch 毫秒，含），缺省不限
     * @param page      页码（从 1 起）
     * @param size      每页大小（1~100）
     * @return 分页结果
     */
    @GetMapping("/logs")
    public Result<PageResult<AuditLog>> listLogs(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(auditService.queryLogs(userId, service, startTime, endTime, page, size));
    }

    /**
     * 校验审计链完整性（防篡改证据链）。
     *
     * @param userId 指定用户则只校验其分片
     * @param fromId 起始 id（含）
     * @param toId   结束 id（含）
     * @param limit  单分片最多校验条数（默认 1000，上限 5000）
     * @return 校验报告
     */
    @GetMapping("/integrity/verify")
    public Result<AuditService.ChainReport> verifyIntegrity(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "fromId", required = false) Long fromId,
            @RequestParam(value = "toId", required = false) Long toId,
            @RequestParam(value = "limit", defaultValue = "1000") int limit) {
        return Result.success(auditService.verifyIntegrity(userId, fromId, toId, limit));
    }
}
