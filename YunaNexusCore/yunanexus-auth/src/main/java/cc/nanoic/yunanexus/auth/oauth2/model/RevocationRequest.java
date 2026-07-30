package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * OAuth2 Token 吊销请求 (RFC 7009 §2.1).
 */
@Data
public class RevocationRequest {

    /** 要吊销的 Token 值 */
    private String token;

    /** Token 类型提示: access_token 或 refresh_token */
    private String tokenTypeHint;
}
