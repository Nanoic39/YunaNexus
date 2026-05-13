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

        Map<String, String> matchedBy = new HashMap<>();
        boolean allow;
        if (allMatch) {
            allow = true;
            for (String t : target) {
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                String matched = matchFirst(owned, t.trim());
                if (matched == null) {
                    allow = false;
                    break;
                }
                matchedBy.put(t.trim(), matched);
            }
        } else {
            allow = false;
            for (String t : target) {
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                String matched = matchFirst(owned, t.trim());
                if (matched != null) {
                    allow = true;
                    matchedBy.put(t.trim(), matched);
                    break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("allow", allow);
        result.put("matchedBy", matchedBy);
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
        List<String> roles = safeList(permissionMapper.listRoleNames(userId));
        List<String> permissions = safeList(permissionMapper.listPermissionCodes(userId));

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("maxRoleLevel", maxLevel == null ? 0 : maxLevel);
        data.put("roles", roles);
        data.put("permissionCodes", permissions);

        if (maxLevel != null && maxLevel >= 9) {
            data.put("wildcard", "*");
        }

        data.put("fieldWhitelist", buildFieldWhitelist(userId, maxLevel));

        return data;
    }

    @Override
    public boolean bindRole(Long userId, String roleName) {
        if (userId == null || !StringUtils.hasText(roleName)) {
            return false;
        }
        return permissionMapper.bindRole(userId, roleName.trim()) >= 0;
    }

    @Override
    public int syncResources(List<Map<String, Object>> resources) {
        if (resources == null || resources.isEmpty()) {
            return 0;
        }

        int synced = 0;
        for (Map<String, Object> resource : resources) {
            String code = asText(resource.get("code"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String name = StringUtils.hasText(asText(resource.get("name"))) ? asText(resource.get("name")) : code;
            String type = StringUtils.hasText(asText(resource.get("type"))) ? asText(resource.get("type")) : "API";
            String path = asText(resource.get("path"));
            Integer status = parseInteger(resource.get("status"));
            if (status == null) {
                status = 1;
            }

            synced += permissionMapper.insertResourceIfAbsent(name.trim(), code.trim(), type.trim(), path, status);
            Long resourceId = permissionMapper.findResourceIdByCode(code.trim());
            if (resourceId == null) {
                continue;
            }

            Object fieldsObj = resource.get("fields");
            if (fieldsObj instanceof Iterable<?> fields) {
                for (Object field : fields) {
                    Map<String, Object> fieldMap = toFieldMap(field);
                    String fieldName = asText(fieldMap.get("fieldName"));
                    if (!StringUtils.hasText(fieldName)) {
                        continue;
                    }
                    String description = asText(fieldMap.get("description"));
                    Integer fieldStatus = parseInteger(fieldMap.get("status"));
                    synced += permissionMapper.insertResourceFieldIfAbsent(resourceId, fieldName.trim(), description, fieldStatus == null ? 1 : fieldStatus);
                }
            }
        }
        return synced;
    }

    private boolean hasAny(List<String> owned, String[] target) {
        if (owned == null || owned.isEmpty() || target == null) {
            return false;
        }
        for (String t : target) {
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (matchFirst(owned, t.trim()) != null) {
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
            if (matchFirst(owned, t.trim()) == null) {
                return false;
            }
        }
        return true;
    }

    private String matchFirst(List<String> patterns, String code) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        for (String p : patterns) {
            if (matches(p, code)) {
                return p;
            }
        }
        return null;
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

    private Integer parseInteger(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildFieldWhitelist(Long userId, Integer maxLevel) {
        Map<String, Object> fieldWhitelist = new HashMap<>();
        List<String> dataResourceCodes = safeList(permissionMapper.listResourceCodesByType(userId, "DATA"));
        for (String code : dataResourceCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            try {
                Long resourceId = permissionMapper.findResourceIdByCode(code);
                if (resourceId == null) {
                    continue;
                }
                fieldWhitelist.put(code, resolveAllowedFields(userId, resourceId, maxLevel));
            } catch (Exception e) {
                fieldWhitelist.put(code, List.of());
            }
        }
        return fieldWhitelist;
    }

    private Object resolveAllowedFields(Long userId, Long resourceId, Integer maxLevel) {
        if (maxLevel != null && maxLevel >= 9) {
            return "*";
        }
        Integer allFields = permissionMapper.countAllFieldsGrant(userId, resourceId);
        if (allFields != null && allFields > 0) {
            return "*";
        }
        return safeList(permissionMapper.listGrantedFieldNamesByUserIdAndResourceId(userId, resourceId));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Map<String, Object> toFieldMap(Object field) {
        if (field instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("fieldName", asText(field));
        result.put("status", 1);
        return result;
    }
}