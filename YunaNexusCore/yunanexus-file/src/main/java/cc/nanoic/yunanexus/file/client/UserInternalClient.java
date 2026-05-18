package cc.nanoic.yunanexus.file.client;

import cc.nanoic.yunanexus.common.web.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "YunaNexus-UserService")
public interface UserInternalClient {
    @PostMapping("/internal/user/avatar")
    Result<Map<String, Object>> updateAvatar(@RequestParam("userId") Long userId,
                                             @RequestParam("avatarUuid") String avatarUuid);
}