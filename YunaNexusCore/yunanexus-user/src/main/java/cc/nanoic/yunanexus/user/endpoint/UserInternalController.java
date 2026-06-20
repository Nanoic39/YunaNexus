package cc.nanoic.yunanexus.user.endpoint;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.common.web.dto.UserCreateDTO;
import cc.nanoic.yunanexus.user.service.UserService;
import cn.hutool.core.util.HexUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/uuid")
    public Result<String> getUuid(@RequestBody byte[] globalId) {
        String uuid = userService.findUserByGlobalId(globalId).getUuid();
        return Result.success(uuid);
    }

    @PutMapping("/avatar")
    public Result<?> updateAvatar(@RequestBody Map<String, String> body) {
        byte[] globalId = HexUtil.decodeHex(body.get("globalId"));
        String avatarUuid = body.get("avatarUuid");
        userService.updateAvatar(globalId, avatarUuid);
        return Result.success(null);
    }
}
