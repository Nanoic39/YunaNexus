package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户中心（yunanexus-user）内部接口客户端（OpenFeign，ADR-021）。<p>
 *
 * 服务名 {@code yunanexus-user} 经 Nacos 服务发现 + LoadBalancer 解析。
 * 契约见 {@code docs/api-contract.md} 第四节。
 *
 * @author yunanexus
 */
@FeignClient(name = "yunanexus-user", contextId = "userProfileSyncFeignClient", path = "/internal/user")
public interface UserProfileSyncFeignClient {

    /**
     * 创建用户资料（幂等）。
     *
     * @param request 用户资料
     * @return {@code data} 为该用户的逻辑分片编号
     */
    @PostMapping("/create")
    Result<Integer> create(@RequestBody UserProfileCreateRequest request);

    /**
     * 幂等回查用户资料。
     *
     * @param userId 全局用户 ID
     * @return {@code data} 为资料，不存在时 {@code null}
     */
    @GetMapping("/byUserId")
    Result<UserProfileView> byUserId(@RequestParam("userId") long userId);
}
