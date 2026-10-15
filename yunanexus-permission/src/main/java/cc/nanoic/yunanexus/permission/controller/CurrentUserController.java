package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.CurrentUserView;
import cc.nanoic.yunanexus.permission.service.CurrentUserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户权限接口（对外，SSO 单次登录后前端确定身份）。<p>
 *
 * 身份来自网关注入的 {@code X-User-Id}，客户端无法指定他人。返回当前用户的
 * RBAC 有效态（启用角色 / 权限码并集 / 可见菜单树），供前端渲染动态菜单、
 * 做管理端入口控制与路由守卫。管理端 CRUD 见 {@code /api/v1/permission/admin/**}。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission")
public class CurrentUserController {

    /** 网关下发的用户 ID 请求头（与 GatewayProperties.userIdHeader 一致） */
    private static final String USER_ID_HEADER = "X-User-Id";

    @Resource
    private CurrentUserService currentUserService;

    /**
     * 查询当前登录用户的角色 / 权限 / 菜单。
     */
    @GetMapping("/me")
    public Result<CurrentUserView> me(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return Result.success(currentUserService.load(requireUserId(userId)));
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效的 " + USER_ID_HEADER + " 请求头");
        }
        return userId;
    }
}
