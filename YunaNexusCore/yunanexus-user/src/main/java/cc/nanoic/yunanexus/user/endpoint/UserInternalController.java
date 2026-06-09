package cc.nanoic.yunanexus.user.endpoint;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.common.web.dto.UserCreateDTO;
import cc.nanoic.yunanexus.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/user")
public class UserInternalController {
    @Resource
    private UserService userService;

    @PostMapping("/create")
    public Result<?> userCreate(@RequestBody UserCreateDTO userCreateDTO) {
        userService.createUser(userCreateDTO);
        return Result.success(null);
    }

    @DeleteMapping("/cancel")
    public Result<?> cancel(@RequestBody byte[] globalId) {
        userService.cancelRegister(globalId);
        return Result.success(null);
    }
}
