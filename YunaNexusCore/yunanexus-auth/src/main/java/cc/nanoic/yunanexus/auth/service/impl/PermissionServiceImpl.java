package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.entity.DTO.PermissionEvaluateRequest;
import cc.nanoic.yunanexus.auth.mapper.PermissionMapper;
import cc.nanoic.yunanexus.auth.service.PermissionService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private TokenService tokenService;

    @Resource
    private PermissionMapper permissionMapper;

    @Override
    public Map<String, Object> evaluate(String accessToken, PermissionEvaluateRequest req) {
        Map<String, Object> parsed = tokenService.parseAccessToken(accessToken);
        if (parsed == null || parsed.get("userId") == null) {
            return null;
        }

        Long userId = parseLong(parsed.get("userId"));
        if (userId == null) {
            return null;
        }

        Integer maxLevel = permissionMapper.findMaxRoleLevel(userId);
        if (maxLevel != null && maxLevel >= 9) {
            Map<String, Object> ok = new HashMap<>();
            ok.put("allow", true);
            ok.put("matchedBy", "*");
            return ok;
        }

        String[] target = req == null ? null : req.getPermissionCodes();
        boolean allMatch = req != null && req.isAllMatch();
        if (target == null || target.length == 0) {
            Map<String, Object> ok = new HashMap<>();
            ok.put("allow", true);
            return ok;
        }

        List<String> owned = permissionMapper.listPermissionCodes(userId);

        boolean allow = allMatch
                ? hasAll(owned, target)
                : hasAny(owned, target);

        Map<String, Object> result = new HashMap<>();
        result.put("allow", allow);
        return result;
    }

    @Override
    public Map<String, Object> snapshot(String accessToken) {
        Map<String, Object> parsed = tokenService.parseAccessToken(accessToken);
        if (parsed == null || parsed.get("userId") == null) {
            return null;
        }

        Long userId = parseLong(parsed.get("userId"));
        if (userId == null) {
            return null;
        }

        Integer maxLevel = permissionMapper.findMaxRoleLevel(userId);
        List<String> roles = permissionMapper.listRoleNames(userId);
        List<String> permissions = permissionMapper.listPermissionCodes(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("maxRoleLevel", maxLevel == null ? 0 : maxLevel);
        data.put("roles", roles);
        data.put("permissionCodes", permissions);

        if (maxLevel != null && maxLevel >= 9) {
            data.put("wildcard", "*");
        }

        return data;
    }

    @Override
    public boolean bindRole(Long userId, String roleName) {
        if (userId == null || !StringUtils.hasText(roleName)) {
            return false;
        }
        return permissionMapper.bindRole(userId, roleName.trim()) >= 0;
    }

    private boolean hasAny(List<String> owned, String[] target) {
        if (owned == null || owned.isEmpty()) {
            return false;
        }
        for (String t : target) {
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (matchesAny(owned, t.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAll(List<String> owned, String[] target) {
        if (target == null) {
            return true;
        }
        for (String t : target) {
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (!matchesAny(owned, t.trim())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAny(List<String> patterns, String code) {
        for (String p : patterns) {
            if (matches(p, code)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String pattern, String code) {
        if (!StringUtils.hasText(pattern) || !StringUtils.hasText(code)) {
            return false;
        }
        pattern = pattern.trim();
        code = code.trim();

        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.equals(code)) {
            return true;
        }

        String[] p = pattern.split(":");
        String[] c = code.split(":");
        if (p.length != c.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if ("*".equals(p[i])) {
                continue;
            }
            if (!p[i].equals(c[i])) {
                return false;
            }
        }
        return true;
    }

    private Long parseLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}