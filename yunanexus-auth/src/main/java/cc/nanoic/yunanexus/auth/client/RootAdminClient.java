package cc.nanoic.yunanexus.auth.client;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 仅服务内网使用的根角色校验。 */
@FeignClient(name="yunanexus-permission", contextId="rootAdminClient")
public interface RootAdminClient {
    @GetMapping("/internal/permission/root-admin")
    Result<Boolean> check(@RequestParam("userId") long userId);
}
