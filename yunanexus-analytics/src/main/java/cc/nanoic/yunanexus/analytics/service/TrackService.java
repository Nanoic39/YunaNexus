package cc.nanoic.yunanexus.analytics.service;

import cc.nanoic.yunanexus.analytics.domain.TrackAccepted;
import cc.nanoic.yunanexus.analytics.domain.TrackEvent;

import java.util.List;

/**
 * 埋点事件接入服务（roadmap 10.1 / 10.2）。<p>
 *
 * 校验、补全事件字段后投递 MQ；失败隔离，埋点异常不影响客户端主链路。
 *
 * @author yunanexus
 */
public interface TrackService {

    /**
     * 接收并投递一批埋点事件。
     *
     * @param events   事件数组（前端 SDK 批量上报）
     * @param clientIp 客户端 IP（网关 / 服务端补全用）
     * @return 接收与丢弃统计
     */
    TrackAccepted track(List<TrackEvent> events, String clientIp);
}
