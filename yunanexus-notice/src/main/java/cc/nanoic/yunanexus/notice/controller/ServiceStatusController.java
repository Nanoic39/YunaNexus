package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.notice.domain.ServiceStatusSummary;
import cc.nanoic.yunanexus.notice.domain.ServiceStatusView;
import cc.nanoic.yunanexus.notice.service.ServiceStatusService;

import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 门户公开服务状态接口，与前端 Mock 地址一致。@author yunanexus */
@RestController
@RequestMapping("/api/v1/status")
public class ServiceStatusController {

    @Resource private ServiceStatusService serviceStatusService;

    @GetMapping
    public Result<ServiceStatusSummary> summary(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int hours) {
        return Result.success(serviceStatusService.getSummary(hours));
    }

    @GetMapping("/{name}/history")
    public Result<ServiceStatusView> history(
            @PathVariable("name") String name,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int hours) {
        return Result.success(serviceStatusService.getHistory(name, hours));
    }

    @GetMapping("/{name}")
    public Result<ServiceStatusView> status(
            @PathVariable("name") String name,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int hours) {
        return Result.success(serviceStatusService.getHistory(name, hours));
    }
}
