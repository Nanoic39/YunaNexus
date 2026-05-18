package cc.nanoic.yunanexus.file.client;

import cc.nanoic.yunanexus.common.web.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "YunaNexus-AuthService")
public interface AuthInternalClient {
    /**
     * 解析用户id信息
     * @param authorization Token
     * @return 解析结果
     */
    @PostMapping("/oauth/parse")
    Result<Map<String, Object>> parse(@RequestHeader(value = "Authorization", required = false) String authorization);
}