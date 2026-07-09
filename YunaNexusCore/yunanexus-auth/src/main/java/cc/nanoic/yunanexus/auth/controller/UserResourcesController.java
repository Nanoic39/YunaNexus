package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.VO.ResourceVO;
import cc.nanoic.yunanexus.auth.service.ResourceService;
import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RequirePermission
@RestController
@RequestMapping("/user")
public class UserResourcesController {

    @Resource
    private ResourceService resourceService;

    /**
     * 获取当前用户的菜单树（仅 type=0 目录 + type=1 菜单）
     * 网关转发 user→auth 服务时需携带 X-User-Uuid / X-User-Permissions 头
     */
    @GetMapping("/menus")
    public Result<List<ResourceVO>> getUserMenus() {
        Set<String> permissions = PermissionContext.getPermissions();
        List<ResourceVO> tree = resourceService.buildMenuTree(permissions);
        return Result.success(tree);
    }

    /**
     * 获取当前用户的按钮权限码列表（type=2 页面资源）
     */
    @GetMapping("/buttons")
    public Result<List<String>> getUserButtons() {
        Set<String> permissions = PermissionContext.getPermissions();
        List<String> codes = resourceService.getUserButtonCodes(permissions);
        return Result.success(codes);
    }

    /**
     * 登录时一次性返回菜单 + 按钮
     */
    @GetMapping("/resources")
    public Result<Map<String, Object>> getUserResources() {
        Set<String> permissions = PermissionContext.getPermissions();
        List<ResourceVO> menus = resourceService.buildMenuTree(permissions);
        List<String> buttons = resourceService.getUserButtonCodes(permissions);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", menus);
        result.put("buttons", buttons);
        return Result.success(result);
    }
}
