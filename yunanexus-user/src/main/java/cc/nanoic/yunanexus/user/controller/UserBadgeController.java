package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.user.domain.UserBadgeView;
import cc.nanoic.yunanexus.user.service.UserBadgeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自助徽章接口（对外，roadmap 5.9）。<p>
 *
 * 徽章由系统 / 运营授予，用户端只读：身份来自网关注入的 {@code X-User-Id}。
 * 授予入口见 {@link InternalUserController#grantBadge}。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/user/badges")
public class UserBadgeController {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Resource
    private UserBadgeService userBadgeService;

    /**
     * 查询当前登录用户的徽章。
     */
    @GetMapping
    public Result<List<UserBadgeView>> list(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return Result.success(userBadgeService.listBadges(requireUserId(userId)));
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效的 " + USER_ID_HEADER + " 请求头");
        }
        return userId;
    }
}
