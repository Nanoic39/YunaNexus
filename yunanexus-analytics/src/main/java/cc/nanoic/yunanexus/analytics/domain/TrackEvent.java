package cc.nanoic.yunanexus.analytics.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 埋点事件模型（{@code POST /api/v1/track} 批量数组元素，详见 {@code docs/analytics.md §4.1}）。<p>
 *
 * 通用字段由 SDK 自动填充：{@code eventType} / {@code eventTime} 为必填，其余可空；
 * 服务端收到后补全 IP 解析的地理位置、UA 解析的设备等，再投递 MQ。
 * 事件中的身份一律使用外部 ID（{@code userId} 即 externalId），内部 ID 不进埋点数据。
 *
 * @author yunanexus
 */
@Data
public class TrackEvent {

    /** 事件唯一 ID（UUID），为空时由服务端补齐 */
    private String eventId;

    /** 事件类型（{@code $page_view} / {@code $click} / 自定义） */
    @NotBlank(message = "eventType 不能为空")
    private String eventType;

    /** 事件发生时间（epoch 毫秒） */
    private Long eventTime;

    /** 匿名 ID（设备级） */
    private String anonymousId;

    /** 登录后的外部 ID（未登录为空） */
    private String userId;

    /** 会话 ID */
    private String sessionId;

    /** 设备指纹 */
    private String deviceId;

    /** 平台：web / ios / android / miniprogram */
    private String platform;

    /** 应用版本 */
    private String appVersion;

    /** 页面 URL */
    private String pageUrl;

    /** 来源页 */
    private String referrer;

    /** 浏览器 UA */
    private String userAgent;

    /** 客户端 IP（服务端补） */
    private String ip;

    /** 国家（服务端按 IP 解析补全） */
    private String country;

    /** 省份（服务端按 IP 解析补全） */
    private String province;

    /** 城市（服务端按 IP 解析补全） */
    private String city;

    /** 入库时间（epoch 毫秒，服务端补） */
    private Long ingestTime;

    /** 业务自定义字段 */
    private Map<String, Object> props;
}
