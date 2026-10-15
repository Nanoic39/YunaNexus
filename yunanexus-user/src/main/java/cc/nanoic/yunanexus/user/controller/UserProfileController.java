package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.user.domain.SelfUpdateProfileRequest;
import cc.nanoic.yunanexus.user.domain.UserProfileView;
import cc.nanoic.yunanexus.user.service.UserProfileService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自助资料接口（对外，roadmap 5.7）。<p>
 *
 * 用户只能查询 / 修改自己的资料：身份来自网关注入的 {@code X-User-Id}，
 * 客户端无法通过请求参数指定他人。内部接口见 {@link InternalUserController}。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/user/profile")
public class UserProfileController {

    /** 网关下发的用户 ID 请求头（与 GatewayProperties.userIdHeader 一致） */
    private static final String USER_ID_HEADER = "X-User-Id";

    @Resource
    private UserProfileService userProfileService;

    /**
     * 查询当前登录用户的资料。
     */
    @GetMapping
    public Result<UserProfileView> profile(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        UserProfileView view = userProfileService.getByUserId(requireUserId(userId));
        if (view == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return Result.success(view);
    }

    /**
     * 修改当前登录用户的资料（昵称 / 头像 / 邮箱 / 手机号，仅更新非空字段）。
     */
    @PutMapping
    public Result<UserProfileView> update(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @Valid @RequestBody SelfUpdateProfileRequest request) {
        long uid = requireUserId(userId);
        userProfileService.updateSelfProfile(uid, request);
        return Result.success(userProfileService.getByUserId(uid));
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效的 " + USER_ID_HEADER + " 请求头");
        }
        return userId;
    }
}
