package cc.nanoic.yunanexus.user.client;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.user.entity.DTO.PermissionEvaluateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "YunaNexus-AuthService")
public interface AuthInternalClient {

    @PostMapping("/oauth/parse")
    Result<Map<String, Object>> parseToken(@RequestHeader("Authorization") String authorization);

    @PostMapping("/permission/evaluate")
    Result<Map<String, Object>> evaluate(@RequestHeader("Authorization") String authorization, @RequestBody PermissionEvaluateRequest req);

    @GetMapping("/permission/snapshot")
    Result<Map<String, Object>> snapshot(@RequestHeader("Authorization") String authorization);

    @PostMapping("/permission/roles/bind")
    Result<?> bindRole(@RequestBody Map<String, Object> req);

    @PostMapping("/permission/resources/sync")
    Result<Map<String, Object>> syncResources(@RequestBody java.util.List<Map<String, Object>> resources);
}
