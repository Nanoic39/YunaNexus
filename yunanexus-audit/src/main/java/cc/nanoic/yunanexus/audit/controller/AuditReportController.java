package cc.nanoic.yunanexus.audit.controller;

import cc.nanoic.yunanexus.audit.domain.AuditReportRequest;
import cc.nanoic.yunanexus.audit.service.AuditService;
import cc.nanoic.yunanexus.common.core.model.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志上报接口（roadmap 8.2）。<p>
 *
 * 供各业务服务内部调用：校验后投递 RocketMQ 异步落库，接口快速返回。
 * 挂 {@code /internal/audit} 前缀（服务间调用，网关不配置该前缀路由、不对外暴露）；
 * 查询接口见 {@code AuditQueryController}（roadmap 8.3，对外 {@code /api/v1/audit}）。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/internal/audit")
public class AuditReportController {

    @Resource
    private AuditService auditService;

    /**
     * 上报一条审计日志（异步落库）。
     */
    /**
     * 批量上报（采集客户端按批发送，降低请求数）。
     *
     * @param requests 事件列表
     * @return 成功接收的条数
     */
    @PostMapping("/report/batch")
    public Result<Integer> reportBatch(@RequestBody java.util.List<AuditReportRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Result.success(0);
        }
        int accepted = 0;
        for (AuditReportRequest request : requests) {
            auditService.report(request);
            accepted++;
        }
        return Result.success(accepted);
    }

    @PostMapping("/report")
    public Result<Void> report(@Valid @RequestBody AuditReportRequest request) {
        auditService.report(request);
        return Result.success();
    }
}
