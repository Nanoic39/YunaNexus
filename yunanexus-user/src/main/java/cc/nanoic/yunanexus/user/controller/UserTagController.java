package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.user.domain.UserTagSetRequest;
import cc.nanoic.yunanexus.user.domain.UserTagView;
import cc.nanoic.yunanexus.user.service.UserTagService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自助标签接口（对外，roadmap 5.9）。<p>
 *
 * 用户只能读写自己的标签：身份来自网关注入的 {@code X-User-Id}。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/user/tags")
public class UserTagController {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Resource
    private UserTagService userTagService;

    /**
     * 查询当前登录用户的标签。
     */
    @GetMapping
    public Result<List<UserTagView>> list(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return Result.success(userTagService.listTags(requireUserId(userId)));
    }

    /**
     * 覆盖式设置当前登录用户的标签（整体替换）。
     */
    @PutMapping
    public Result<List<UserTagView>> set(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @Valid @RequestBody UserTagSetRequest request) {
        long uid = requireUserId(userId);
        userTagService.setTags(uid, request.getTags());
        return Result.success(userTagService.listTags(uid));
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效的 " + USER_ID_HEADER + " 请求头");
        }
        return userId;
    }
}
