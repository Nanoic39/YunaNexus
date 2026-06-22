package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.ApiEndpoint;
import cc.nanoic.yunanexus.auth.entity.DTO.EndpointConfigItem;
import cc.nanoic.yunanexus.auth.entity.DTO.EndpointReportRequest;
import cc.nanoic.yunanexus.auth.mapper.ApiEndpointMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApiEndpointService {

    private static final Logger log = LoggerFactory.getLogger(ApiEndpointService.class);
    private static final String UPDATE_CHANNEL = "auth:endpoints:updated";

    @Resource
    private ApiEndpointMapper apiEndpointMapper;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 服务启动上报接口列表。
     * 已存在则仅更新 reportedAt；不存在则插入（source=0 自动上报）。
     * 返回该服务的全量权威配置。
     */
    public List<EndpointConfigItem> reportEndpoints(EndpointReportRequest req) {
        String serviceName = req.getServiceName();
        List<EndpointReportRequest.EndpointItem> endpoints = req.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            log.info("{} 上报 0 个接口", serviceName);
        } else {
            log.info("{} 上报 {} 个接口", serviceName, endpoints.size());
        }

        // 查出现有记录
        List<ApiEndpoint> existing = apiEndpointMapper.selectList(
                new LambdaQueryWrapper<ApiEndpoint>()
                        .eq(ApiEndpoint::getServiceName, serviceName));
        // 建索引 (method + path) → entity
        Map<String, ApiEndpoint> existingMap = existing.stream()
                .collect(Collectors.toMap(e -> e.getHttpMethod() + " " + e.getPathPattern(), e -> e));

        for (EndpointReportRequest.EndpointItem item : endpoints) {
            String key = item.getHttpMethod() + " " + item.getPathPattern();
            ApiEndpoint existingEp = existingMap.get(key);
            if (existingEp != null) {
                // 已存在：只更新 reportedAt，不覆盖已有的 requiredCode 和 status
                existingEp.setReportedAt(java.time.LocalDateTime.now());
                apiEndpointMapper.updateById(existingEp);
            } else {
                // 新接口：插入
                ApiEndpoint ep = new ApiEndpoint();
                ep.setServiceName(serviceName);
                ep.setHttpMethod(item.getHttpMethod());
                ep.setPathPattern(item.getPathPattern());
                ep.setRequiredCode(item.getRequiredCode());
                ep.setDescription(item.getDescription());
                ep.setSource(0);
                ep.setStatus(1);
                ep.setReportedAt(java.time.LocalDateTime.now());
                apiEndpointMapper.insert(ep);
            }
        }
        // 返回全量配置
        return getServiceConfig(serviceName);
    }

    /**
     * 查询指定服务的接口配置（返回启用状态 + 所需权限码）
     */
    public List<EndpointConfigItem> getServiceConfig(String serviceName) {
        List<ApiEndpoint> endpoints = apiEndpointMapper.selectList(
                new LambdaQueryWrapper<ApiEndpoint>()
                        .eq(ApiEndpoint::getServiceName, serviceName));
        return endpoints.stream()
                .map(e -> new EndpointConfigItem(
                        e.getHttpMethod(), e.getPathPattern(),
                        e.getRequiredCode(), e.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 管理端更新接口权限后，发布 Redis 通知
     */
    public void notifyServiceUpdate(String serviceName) {
        RTopic topic = redissonClient.getTopic(UPDATE_CHANNEL);
        topic.publish(serviceName);
        log.info("已发布接口更新通知: {}", serviceName);
    }
}
