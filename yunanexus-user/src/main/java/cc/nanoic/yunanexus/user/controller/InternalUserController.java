package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.user.domain.CreateUserProfileRequest;
import cc.nanoic.yunanexus.user.domain.UpdateUserProfileRequest;
import cc.nanoic.yunanexus.user.domain.UserBadgeGrantRequest;
import cc.nanoic.yunanexus.user.domain.UserProfileView;
import cc.nanoic.yunanexus.user.service.UserBadgeService;
import cc.nanoic.yunanexus.user.service.UserProfileService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户中心内部接口（roadmap 5.6）。<p>
 *
 * 仅供服务间调用（认证中心注册 outbox 补偿），不对外暴露：网关未配置本前缀路由，
 * 生产环境应通过网络策略限制访问来源。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private UserBadgeService userBadgeService;

    @PostMapping("/create")
    public Result<Integer> create(@Valid @RequestBody CreateUserProfileRequest request) {
        return Result.success(userProfileService.createUserProfile(request.getUserId(), request));
    }

    @GetMapping("/byUserId")
    public Result<UserProfileView> byUserId(@RequestParam("userId") long userId) {
        return Result.success(userProfileService.getByUserId(userId));
    }

    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        userProfileService.updateProfile(request.getUserId(), request);
        return Result.success();
    }

    /**
     * 授予用户徽章（系统 / 运营调用，幂等）。
     */
    @PostMapping("/badges/grant")
    public Result<Void> grantBadge(@Valid @RequestBody UserBadgeGrantRequest request) {
        userBadgeService.grantBadge(request.getUserId(), request);
        return Result.success();
    }
}
