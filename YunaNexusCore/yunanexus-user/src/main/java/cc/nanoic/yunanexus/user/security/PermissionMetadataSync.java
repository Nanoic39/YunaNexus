package cc.nanoic.yunanexus.user.security;

import cc.nanoic.yunanexus.common.security.annotation.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.user.client.AuthInternalClient;
import jakarta.annotation.Resource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PermissionMetadataSync {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private AuthInternalClient authInternalClient;

    @Value("${yunanexus.security.permission-sync.enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        syncOnce();
    }

    @Scheduled(fixedDelayString = "${yunanexus.security.permission-sync.fixed-delay-ms:300000}")
    public void scheduledSync() {
        syncOnce();
    }

    private void syncOnce() {
        if (!enabled) {
            return;
        }

        List<Map<String, Object>> resources = buildLocalResources();
        if (resources.isEmpty()) {
            return;
        }

        try {
            Result<Map<String, Object>> resp = authInternalClient.syncResources(resources);
            if (resp == null || resp.getCode() != R.SUCCESS.getCode()) {
                return;
            }
        } catch (Exception ignored) {
        }
    }

    private List<Map<String, Object>> buildLocalResources() {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        registerAnnotatedApiResources(merged);
        return new ArrayList<>(merged.values());
    }

    private void registerAnnotatedApiResources(Map<String, Map<String, Object>> merged) {
        Map<String, Object> controllerBeans = applicationContext.getBeansWithAnnotation(RestController.class);
        for (Object bean : controllerBeans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null) {
                continue;
            }

            String classPath = extractClassPath(targetClass);
            for (Method method : targetClass.getMethods()) {
                RequirePermission requirePermission = AnnotatedElementUtils.findMergedAnnotation(method,
                        RequirePermission.class);
                if (requirePermission == null || requirePermission.value().length == 0) {
                    continue;
                }

                String methodPath = extractMethodPath(method);
                String fullPath = normalizePath(classPath, methodPath);
                for (String code : requirePermission.value()) {
                    if (!StringUtils.hasText(code)) {
                        continue;
                    }

                    String normalizedCode = code.trim();
                    Map<String, Object> resource = new LinkedHashMap<>();
                    resource.put("name", normalizedCode);
                    resource.put("code", normalizedCode);
                    resource.put("type", "API");
                    resource.put("path", fullPath);
                    resource.put("status", 1);
                    merged.putIfAbsent(normalizedCode, resource);
                }
            }
        }
    }


    private String extractClassPath(Class<?> targetClass) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequestMapping.class);
        return extractFirstPath(mapping == null ? null : mapping.path(), mapping == null ? null : mapping.value());
    }

    private String extractMethodPath(Method method) {
        GetMapping getMapping = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (getMapping != null) {
            return extractFirstPath(getMapping.path(), getMapping.value());
        }

        PostMapping postMapping = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (postMapping != null) {
            return extractFirstPath(postMapping.path(), postMapping.value());
        }

        PutMapping putMapping = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (putMapping != null) {
            return extractFirstPath(putMapping.path(), putMapping.value());
        }

        DeleteMapping deleteMapping = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (deleteMapping != null) {
            return extractFirstPath(deleteMapping.path(), deleteMapping.value());
        }

        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        return extractFirstPath(requestMapping == null ? null : requestMapping.path(),
                requestMapping == null ? null : requestMapping.value());
    }

    private String extractFirstPath(String[] paths, String[] values) {
        if (paths != null) {
            for (String path : paths) {
                if (StringUtils.hasText(path)) {
                    return path.trim();
                }
            }
        }
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private String normalizePath(String classPath, String methodPath) {
        String left = StringUtils.hasText(classPath) ? classPath.trim() : "";
        String right = StringUtils.hasText(methodPath) ? methodPath.trim() : "";

        if (!left.startsWith("/") && StringUtils.hasText(left)) {
            left = "/" + left;
        }
        if (!right.startsWith("/") && StringUtils.hasText(right)) {
            right = "/" + right;
        }

        String path = (left + right).replaceAll("/+", "/");
        return StringUtils.hasText(path) ? path : "/";
    }
}