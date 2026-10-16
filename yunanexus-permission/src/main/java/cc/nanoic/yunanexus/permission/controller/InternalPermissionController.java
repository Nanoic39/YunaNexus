package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.DataScopeResponse;
import cc.nanoic.yunanexus.permission.domain.FieldCheckRequest;
import cc.nanoic.yunanexus.permission.domain.FieldCheckResponse;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.domain.PermissionCheckRequest;
import cc.nanoic.yunanexus.permission.domain.PermissionCheckResponse;
import cc.nanoic.yunanexus.permission.service.AuthorizationService;
import cc.nanoic.yunanexus.permission.service.BindingService;
import cc.nanoic.yunanexus.permission.service.DataScopeService;
import cc.nanoic.yunanexus.permission.service.FieldRuleService;
import cc.nanoic.yunanexus.permission.service.MenuService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 鉴权判定内部接口（roadmap 6.8 / Phase A / Phase C）。<p>
 *
 * 仅供服务间调用（网关 / 各服务），不对外暴露：网关未配置 {@code /internal/**} 路由，
 * 生产环境应通过网络策略限制访问来源。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/internal/permission")
public class InternalPermissionController {
    @Resource
    private cc.nanoic.yunanexus.permission.mapper.PermRoleMapper roleMapper;

    @GetMapping("/root-admin")
    public Result<Boolean> rootAdmin(@RequestParam("userId") long userId) {
        return Result.success(userId > 0 && roleMapper.countRootAdmin(userId) > 0);
    }


    @GetMapping("/super-admin")
    public Result<Boolean> superAdmin(@RequestParam("userId") long userId) {return Result.success(userId>0&&roleMapper.countSuperAdmin(userId)>0);}
    @Resource private org.springframework.jdbc.core.JdbcTemplate db;
    @GetMapping("/has-code")
    public Result<Boolean> hasCode(@RequestParam("userId") long userId,@RequestParam("code") String code){return Result.success(userId>0&&code!=null&&code.length()<=128&&db.queryForObject("SELECT COUNT(*) FROM perm_user_role ur JOIN perm_role r ON r.id=ur.role_id JOIN perm_role_permission rp ON rp.role_id=r.id JOIN perm_permission p ON p.id=rp.permission_id WHERE ur.user_id=? AND r.status=1 AND p.status=1 AND BINARY p.perm_code=?",Integer.class,userId,code)>0);}
    @PostMapping("/default-role")
    public Result<Void> bindDefault(@RequestParam("userId") long userId) {
        if(userId<=0)throw new IllegalArgumentException("Invalid user");
        if(db.queryForObject("SELECT COUNT(*) FROM perm_role WHERE role_code='user' AND status=1",Integer.class)!=1)throw new IllegalStateException("Default role unavailable");
        db.update("INSERT IGNORE INTO perm_user_role(user_id,role_id) SELECT ?,id FROM perm_role WHERE role_code='user' AND status=1",userId);
        return Result.success();
    }
    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private DataScopeService dataScopeService;

    @Resource
    private FieldRuleService fieldRuleService;

    @Resource
    private MenuService menuService;

    @Resource
    private BindingService bindingService;

    @PostMapping("/check")
    public Result<PermissionCheckResponse> check(@Valid @RequestBody PermissionCheckRequest request) {
        boolean allowed = authorizationService.check(
                request.getUserId(), request.getResource(), request.getAction(),
                request.getResourceType(), request.getResourceId(), request.getContext());
        return Result.success(PermissionCheckResponse.of(allowed));
    }

    @GetMapping("/data-scope")
    public Result<DataScopeResponse> dataScope(@RequestParam("userId") long userId) {
        return Result.success(dataScopeService.resolve(userId));
    }

    @GetMapping("/user-dept")
    public Result<Long> userDept(@RequestParam("userId") long userId) {
        return Result.success(bindingService.getUserDeptId(userId));
    }

    @PostMapping("/field-check")
    public Result<FieldCheckResponse> fieldCheck(@Valid @RequestBody FieldCheckRequest request) {
        return Result.success(fieldRuleService.resolve(
                request.getUserId(), request.getResourceType(), request.getAccess()));
    }

    @GetMapping("/menus")
    public Result<List<MenuTreeNode>> menus(@RequestParam("userId") long userId) {
        return Result.success(menuService.userMenuTree(userId));
    }
}
