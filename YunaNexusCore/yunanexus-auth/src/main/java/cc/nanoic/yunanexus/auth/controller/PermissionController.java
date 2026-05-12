package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.PermissionEvaluateRequest;
import cc.nanoic.yunanexus.auth.service.PermissionService;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/permission")
public class PermissionController {
    @Resource
    private PermissionService permissionService;

    /**
     *
     * @param authorization
     * @param permissionEvaluateRequest
     * @return
     */
    @PostMapping("/evaluate")
    public Result<?> evaluate(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody PermissionEvaluateRequest permissionEvaluateRequest) {
        String accessToken = extractBearer(authorization);
        if (accessToken == null) {
            return Result.fail(R.PARAM_ERROR, "Authorization格式错误");
        }

        Map<String, Object> data = permissionService.evaluate(accessToken, permissionEvaluateRequest);
        if (data == null) {
            return Result.fail(R.NOT_LOGIN, "token无效或已过期");
        }
        return Result.success(data);
    }

    /**
     *
     * @param authorization
     * @return
     */
    @GetMapping("/snapshot")
    public Result<?> snapshot(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = extractBearer(authorization);
        if (accessToken == null) {
            return Result.fail(R.PARAM_ERROR, "Authorization格式错误");
        }

        Map<String, Object> data = permissionService.snapshot(accessToken);
        if (data == null) {
            return Result.fail(R.NOT_LOGIN, "token无效或已过期");
        }
        return Result.success(data);
    }

    /**
     * 绑定角色
     * @param req 绑定信息 userId, Long; roleName, String
     * @return 绑定结果
     */
    @PostMapping("/roles/bind")
    public Result<?> bindRole(@RequestBody Map<String, Object> req) {
        Object userIdObj = req == null ? null : req.get("userId");
        Object roleNameObj = req == null ? null : req.get("roleName"); // TODO: 调整为roleId
        Long userId = parseLong(userIdObj);
        String roleName = roleNameObj == null ? null : String.valueOf(roleNameObj);
        if (userId == null || !StringUtils.hasText(roleName)) {
            return Result.fail(R.PARAM_ERROR, "userId/roleName不能为空");
        }

        boolean ok = permissionService.bindRole(userId, roleName);
        Map<String, Object> data = new HashMap<>();
        data.put("bound", ok);
        return Result.success(data);
    }

    private String extractBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
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
