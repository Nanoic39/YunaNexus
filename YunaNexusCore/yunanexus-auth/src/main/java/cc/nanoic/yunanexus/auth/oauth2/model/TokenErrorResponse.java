package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * RFC 6749 §5.2 Token 端点错误响应.
 */
@Data
public class TokenErrorResponse {
    private String error;
    private String errorDescription;
}
