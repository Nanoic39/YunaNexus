package cc.nanoic.yunanexus.notice.client;

import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 元数据中心（yunanexus-meta）内部调用客户端（OpenFeign）。<p>
 *
 * 发送通知时为 {@code notice_record} 领取全局唯一通知 ID。服务名 {@code yunanexus-meta}
 * 经 Nacos 服务发现 + LoadBalancer 解析。
 *
 * @author yunanexus
 */
@FeignClient(name = "yunanexus-meta", contextId = "noticeMetaIdClient")
public interface MetaIdClient {

    /**
     * 领取下一个全局内部 ID。
     *
     * @param bizTag 业务标识，通知 ID 固定为 {@code notice}
     * @return 全局唯一、趋势递增的内部 ID（包装于 Result）
     */
    @PostMapping("/api/v1/segment/next")
    Result<Long> nextId(@RequestParam("bizTag") String bizTag);
}
