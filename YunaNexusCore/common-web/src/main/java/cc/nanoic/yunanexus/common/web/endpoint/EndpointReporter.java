package cc.nanoic.yunanexus.common.web.endpoint;

import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务启动时扫描所有带 @RequirePermission 的接口，上报到 Auth 管理中心。
 * 同时订阅 Redis 更新频道，实时更新本地缓存。
 */
public class EndpointReporter {

    private static final Logger log = LoggerFactory.getLogger(EndpointReporter.class);

    private final String serviceName;
    private final RedissonClient redissonClient;
    private final RequestMappingHandlerMapping handlerMapping;

    /**
     * 本地缓存: path → (httpMethod → {requiredCode, status})
     */
    private final Map<String, Map<String, EndpointEntry>> localCache = new ConcurrentHashMap<>();

    public EndpointReporter(String serviceName, RedissonClient redissonClient,
            RequestMappingHandlerMapping handlerMapping) {
        this.serviceName = serviceName;
        this.redissonClient = redissonClient;
        this.handlerMapping = handlerMapping;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reportEndpoints();
        subscribeUpdates();
    }

    /**
     * 扫描全部 @RequestMapping + @RequirePermission，上报到 Auth
     */
    private void reportEndpoints() {
        Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
        List<Map<String, Object>> endpoints = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.entrySet()) {
            HandlerMethod handler = entry.getValue();
            RequirePermission rp = getRequirePermission(handler);
            if (rp == null) {
                continue; // 无需权限的接口不上报
            }

            Set<String> patterns = entry.getKey().getDirectPaths();
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();

            for (String path : patterns) {
                for (RequestMethod method : methods) {
                    Map<String, Object> ep = new LinkedHashMap<>();
                    ep.put("httpMethod", method.name());
                    ep.put("pathPattern", path);
                    ep.put("requiredCode", String.join(",", rp.value()));
                    ep.put("description", "");
                    endpoints.add(ep);
                }
            }
        }

        log.info("{} 扫描到 {} 个需权限的接口", serviceName, endpoints.size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceName", serviceName);
        body.put("endpoints", endpoints);
        // 通过内部调用上报（实际由各服务配置的 FeignClient 或 RestTemplate 完成）
        applyRemoteConfig(endpoints);
    }

    /**
     * 将扫描结果应用到本地缓存（兜底——Auth 不可用时也能鉴权）
     */
    private void applyRemoteConfig(List<Map<String, Object>> endpoints) {
        for (Map<String, Object> ep : endpoints) {
            String method = (String) ep.get("httpMethod");
            String path = (String) ep.get("pathPattern");
            String code = (String) ep.get("requiredCode");
            localCache.computeIfAbsent(path, k -> new ConcurrentHashMap<>())
                    .put(method, new EndpointEntry(code, 1));
        }
        log.info("{} 本地缓存已加载 {} 条接口权限配置", serviceName, endpoints.size());
    }

    /**
     * 新增：将上报数据发送到 Auth 并接收返回的权威配置
     */
    public void applyAuthConfig(List<Map<String, Object>> authEndpoints) {
        localCache.clear();
        for (Map<String, Object> ep : authEndpoints) {
            String method = (String) ep.get("httpMethod");
            String path = (String) ep.get("pathPattern");
            String code = (String) ep.get("requiredCode");
            int status = ep.get("status") != null ? ((Number) ep.get("status")).intValue() : 1;
            localCache.computeIfAbsent(path, k -> new ConcurrentHashMap<>())
                    .put(method, new EndpointEntry(code, status));
        }
        log.info("{} 已从 Auth 同步 {} 条接口权限配置", serviceName, authEndpoints.size());
    }

    /**
     * 订阅 Redis 频道，收到更新后重拉配置
     */
    private void subscribeUpdates() {
        RTopic topic = redissonClient.getTopic("auth:endpoints:updated");
        topic.addListener(String.class, (channel, updatedService) -> {
            if (serviceName.equals(updatedService)) {
                log.info("收到接口权限更新通知, 准备重拉配置...");
                // 实际调用 Auth 的 GET /internal/endpoints/{serviceName} 由各服务自行实现
            }
        });
        log.info("{} 已订阅 auth:endpoints:updated", serviceName);
    }

    /**
     * 鉴权: 检查当前用户是否有权限访问指定接口
     * 
     * @return true=放行
     */
    public boolean checkPermission(String path, String httpMethod, Set<String> userPermissions) {
        if (userPermissions.contains("*:*:*:*"))
            return true;

        Map<String, EndpointEntry> methodMap = localCache.get(path);
        if (methodMap == null || methodMap.isEmpty()) {
            // 未注册的接口默认放行（不在管理范围内）
            return true;
        }

        EndpointEntry entry = methodMap.get(httpMethod);
        if (entry == null) {
            // 该 HTTP Method 未注册，检查是否有 ANY 匹配
            entry = methodMap.get("*");
            if (entry == null) {
                return true;
            }

        }

        // status=0 停用不校验
        if (entry.status == 0) {
            return true;
        }

        // 检查权限码
        if (entry.requiredCode == null || entry.requiredCode.isEmpty()) {
            return true;
        }
            
        for (String code : entry.requiredCode.split(",")) {
            if (userPermissions.contains(code.trim())) {
                return true;
            } 
        }
        return false;
    }

    /**
     * 获取 handler 上的 @RequirePermission 注解（方法级优先）
     */
    private RequirePermission getRequirePermission(HandlerMethod handler) {
        RequirePermission rp = handler.getMethodAnnotation(RequirePermission.class);
        if (rp != null) {
            return rp;
        }
        return handler.getBeanType().getAnnotation(RequirePermission.class);
    }

    /**
     * 接口权限条目
     */
    public static class EndpointEntry {
        public final String requiredCode;
        public final int status;

        public EndpointEntry(String requiredCode, int status) {
            this.requiredCode = requiredCode;
            this.status = status;
        }
    }
}
