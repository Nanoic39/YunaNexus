package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.common.web.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "YunaNexus-UserService")
public interface UserInternalClient {

    @PostMapping("/oauth/verify")
    Result<Map<String, Object>> verifyUser(@RequestBody Map<String, String> req);
}
