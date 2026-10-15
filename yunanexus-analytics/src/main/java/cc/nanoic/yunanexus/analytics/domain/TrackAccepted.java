package cc.nanoic.yunanexus.analytics.domain;

import lombok.Data;

/**
 * 埋点上报响应（{@code POST /api/v1/track} 返回）。<p>
 *
 * 埋点失败隔离：即使 MQ 不可用也返回成功（事件降级丢弃），客户端不因埋点失败阻塞主链路。
 *
 * @author yunanexus
 */
@Data
public class TrackAccepted {

    /** 接收并投递的事件数 */
    private int accepted;

    /** 因格式非法被丢弃的事件数 */
    private int rejected;

    public static TrackAccepted of(int accepted, int rejected) {
        TrackAccepted result = new TrackAccepted();
        result.accepted = accepted;
        result.rejected = rejected;
        return result;
    }
}
