package cc.nanoic.yunanexus.auth.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息（Redis 存储模型）。<p>
 *
 * 以 JSON 字符串形式存于 {@code auth:{sid}:session:v2}，并维护反向索引
 * {@code auth:user_sessions:{u-userId}:v2} → ZSET(sid → 会话过期毫秒)。
 * 时间字段统一用 epoch 毫秒，避免 Jackson 日期序列化差异。
 *
 * @author yunanexus
 */
@Data
@NoArgsConstructor
public class SessionInfo {

    /** 会话 ID（SID） */
    private String sid;

    /** 全局内部用户 ID（仅内部存储，不下发）。序列化为十进制字符串，避免 Lua/JSON number 超过 2^53 精度丢失 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long userId;

    /** 外部 ID（对外标识） */
    private String externalId;

    /** 账号 */
    private String username;

    /** 客户端标识 */
    private String clientCode;

    /** 设备指纹 */
    private String deviceId;

    /** access token 的 jti */
    private String accessJti;

    /** refresh token 的 jti */
    private String refreshJti;

    /** 登录时间（epoch 毫秒） */
    private long loginTimeMillis;

    /** 会话过期时间（epoch 毫秒），cap = min(refreshExpiresAt, now + sessionTtlSeconds*1000) */
    private long expireAtMillis;

    /** access token 过期时间（epoch 毫秒） */
    private long accessExpiresAtMillis;

    /** refresh token 过期时间（epoch 毫秒） */
    private long refreshExpiresAtMillis;

    /** 账号纪元（account epoch）。旧数据缺失时为 null，必须重新登录 */
    private String accountEpoch;

    /**
     * 兼容旧版 10 参构造（epoch=null，旧数据拒绝；不猜测令牌过期时间，字段默认 0）。
     * 新 create 必须校验 &gt; now，旧数据需重登。
     */
    public SessionInfo(String sid, long userId, String externalId, String username,
                       String clientCode, String deviceId, String accessJti,
                       String refreshJti, long loginTimeMillis, long expireAtMillis) {
        this(sid, userId, externalId, username, clientCode, deviceId, accessJti,
                refreshJti, loginTimeMillis, expireAtMillis, 0, 0, null);
    }

    /**
     * 完整 12 参构造（epoch=null，旧数据拒绝；新数据请使用 13 参构造）。
     */
    public SessionInfo(String sid, long userId, String externalId, String username,
                       String clientCode, String deviceId, String accessJti,
                       String refreshJti, long loginTimeMillis, long expireAtMillis,
                       long accessExpiresAtMillis, long refreshExpiresAtMillis) {
        this(sid, userId, externalId, username, clientCode, deviceId, accessJti,
                refreshJti, loginTimeMillis, expireAtMillis, accessExpiresAtMillis,
                refreshExpiresAtMillis, null);
    }

    /**
     * 完整 13 参构造（含 accountEpoch）。
     */
    public SessionInfo(String sid, long userId, String externalId, String username,
                       String clientCode, String deviceId, String accessJti,
                       String refreshJti, long loginTimeMillis, long expireAtMillis,
                       long accessExpiresAtMillis, long refreshExpiresAtMillis,
                       String accountEpoch) {
        this.sid = sid;
        this.userId = userId;
        this.externalId = externalId;
        this.username = username;
        this.clientCode = clientCode;
        this.deviceId = deviceId;
        this.accessJti = accessJti;
        this.refreshJti = refreshJti;
        this.loginTimeMillis = loginTimeMillis;
        this.expireAtMillis = expireAtMillis;
        this.accessExpiresAtMillis = accessExpiresAtMillis;
        this.refreshExpiresAtMillis = refreshExpiresAtMillis;
        this.accountEpoch = accountEpoch;
    }
}
