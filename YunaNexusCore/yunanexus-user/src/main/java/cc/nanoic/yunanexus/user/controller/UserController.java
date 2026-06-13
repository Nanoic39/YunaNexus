package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.user.entity.VO.UserProfileVO;
import cc.nanoic.yunanexus.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        byte[] globalId = PermissionContext.getGlobalId();
        if (globalId == null) {
            throw new BusinessException(R.NOT_LOGIN, "请先登录");
        }
        return Result.success(userService.getUserProfile(globalId, true));
    }
}