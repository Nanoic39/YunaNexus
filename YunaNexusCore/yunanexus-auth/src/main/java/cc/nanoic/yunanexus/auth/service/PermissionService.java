package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.DTO.PermissionEvaluateRequest;

import java.util.Map;

public interface PermissionService {
    Map<String, Object> evaluate(String accessToken, PermissionEvaluateRequest req);
    Map<String, Object> snapshot(String accessToken);
    boolean bindRole(Long userId, String roleName);
}