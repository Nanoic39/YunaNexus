package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.EndpointConfigItem;
import cc.nanoic.yunanexus.auth.entity.DTO.EndpointReportRequest;
import cc.nanoic.yunanexus.auth.service.ApiEndpointService;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/endpoints")
public class ApiEndpointController {

    @Resource
    private ApiEndpointService apiEndpointService;

    /**
     * 各服务启动时上报接口列表（内部调用）
     */
    @PostMapping("/report")
    public Result<List<EndpointConfigItem>> report(@RequestBody EndpointReportRequest request) {
        List<EndpointConfigItem> config = apiEndpointService.reportEndpoints(request);
        return Result.success(config);
    }

    /**
     * 获取指定服务的全量接口配置（内部调用 / 服务拉取更新）
     */
    @GetMapping("/{serviceName}")
    public Result<List<EndpointConfigItem>> getServiceConfig(@PathVariable String serviceName) {
        List<EndpointConfigItem> config = apiEndpointService.getServiceConfig(serviceName);
        return Result.success(config);
    }

    /**
     * 管理端修改接口配置后触发推送通知（由管理端控制器调用）
     */
    @PostMapping("/publish/{serviceName}")
    public Result<Void> publishUpdate(@PathVariable String serviceName) {
        apiEndpointService.notifyServiceUpdate(serviceName);
        return Result.success(null);
    }
}
