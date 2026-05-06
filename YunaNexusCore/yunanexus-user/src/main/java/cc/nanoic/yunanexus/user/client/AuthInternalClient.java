package cc.nanoic.yunanexus.user.client;

import cc.nanoic.yunanexus.common.web.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "YunaNexus-AuthService")
public interface AuthInternalClient {

    @PostMapping("/oauth/parse")
    Result<Map<String, Object>> parseToken(@RequestHeader("Authorization") String authorization);
}
