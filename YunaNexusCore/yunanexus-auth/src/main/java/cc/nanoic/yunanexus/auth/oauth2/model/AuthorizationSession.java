package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * Redis 中存储的授权会话 — 在 GET /oauth2/authorize 校验通过后创建.
 * <p>TTL: 10 分钟，授权成功或过期后自动删除.</p>
 */
@Data
public class AuthorizationSession {

    /** 会话唯一标识（32 位安全随机字符串） */
    private String sessionId;

    /** 客户端 UUID */
    private String clientId;

    /** 客户端名称（冗余存储，减少前端查询） */
    private String clientName;

    /** 回调地址（已校验通过） */
    private String redirectUri;

    /** 请求的授权范围 */
    private String scope;

    /** 客户端传入的 state 参数（CSRF 防护，原样回传） */
    private String state;

    /** PKCE code_challenge（S256 后的 Base64URL 值） */
    private String codeChallenge;

    /** PKCE 方法: S256 或 plain */
    private String codeChallengeMethod;

    /** 服务端生成的 CSRF nonce */
    private String nonce;

    /** 会话创建时间（Unix 秒） */
    private long createdAt;

    /** 授权用户 globalId（hex 编码），用户在授权页面确认后设置 */
    private String userGlobalId;
}
