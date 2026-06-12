package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.common.web.dto.UserCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "YunaNexus-UserService")
public interface UserInternalClient {

    @PostMapping("/internal/user/create")
    Result<?> createUser(@RequestBody UserCreateDTO userCreateDTO);

    @DeleteMapping("/internal/user/cancel")
    Result<?> cancelUser(@RequestBody byte[] globalId);

    @PostMapping("/internal/user/uuid")
    Result<String> getUuid(@RequestBody byte[] globalId);
}
