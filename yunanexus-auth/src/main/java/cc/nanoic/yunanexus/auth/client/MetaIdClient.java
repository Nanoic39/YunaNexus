package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 元数据中心（yunanexus-meta）内部调用客户端（OpenFeign，ADR-005）。<p>
 *
 * 注册时为 {@code auth_user} 领取全局唯一内部用户 ID。服务名 {@code yunanexus-meta}
 * 经 Nacos 服务发现 + LoadBalancer 解析（ADR-011）。
 *
 * @author yunanexus
 */
@FeignClient(name = "yunanexus-meta", contextId = "metaIdClient")
public interface MetaIdClient {

    /**
     * 领取下一个全局内部 ID。
     *
     * @param bizTag 业务标识，用户 ID 固定为 {@code user}
     * @return 全局唯一、趋势递增的内部 ID（包装于 Result）
     */
    @PostMapping("/api/v1/segment/next")
    Result<Long> nextId(@RequestParam("bizTag") String bizTag);
}
