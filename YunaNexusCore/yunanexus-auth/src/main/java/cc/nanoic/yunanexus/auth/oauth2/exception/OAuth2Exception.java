package cc.nanoic.yunanexus.auth.oauth2.exception;

import cc.nanoic.yunanexus.auth.oauth2.model.TokenErrorResponse;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import lombok.Getter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * RFC 6749 标准 OAuth2 异常基类.
 * <p>
 * 授权端点错误 → 302 重定向到 redirect_uri，带 error/error_description/state 参数.
 * Token 端点错误 → 返回 JSON {@code {"error": "xxx", "error_description": "xxx"}}，HTTP 400/401.
 * </p>
 */
@Getter
public class OAuth2Exception extends BusinessException {

    /** RFC 6749 标准错误码 */
    private final String errorCode;

    /** 人类可读的错误描述 */
    private final String errorDescription;

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 授权端点专用：错误回跳地址（Token 端点异常时为 null） */
    private final String redirectUri;

    /** 授权端点专用：回传客户端的 state 参数 */
    private final String state;

    public OAuth2Exception(String errorCode, String errorDescription,
                           int httpStatus, String redirectUri, String state) {
        super(R.PARAM_ERROR, errorDescription);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
        this.httpStatus = httpStatus;
        this.redirectUri = redirectUri;
        this.state = state;
    }

    /**
     * Token 端点异常（无 redirect_uri，返回 JSON 错误）.
     */
    public static OAuth2Exception tokenError(String errorCode, String errorDescription, int httpStatus) {
        return new OAuth2Exception(errorCode, errorDescription, httpStatus, null, null);
    }

    /**
     * 授权端点异常（需 302 重定向到 redirect_uri）.
     */
    public static OAuth2Exception authorizationError(
            String errorCode, String errorDescription, String redirectUri, String state) {
        return new OAuth2Exception(errorCode, errorDescription, 302, redirectUri, state);
    }

    /**
     * 判断是否为授权端点错误（需要重定向到 redirect_uri）.
     */
    public boolean isRedirectError() {
        return redirectUri != null && !redirectUri.isEmpty();
    }

    /**
     * 构建授权端点的错误重定向 URL.
     * <p>格式: {@code redirectUri?error=xxx&error_description=xxx&state=xxx}</p>
     */
    public String buildErrorRedirectUri() {
        StringBuilder sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? "&" : "?");
        sb.append("error=").append(URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
        if (errorDescription != null && !errorDescription.isEmpty()) {
            sb.append("&error_description=").append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
        }
        if (state != null && !state.isEmpty()) {
            sb.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * 构建 Token 端点的 JSON 错误响应.
     */
    public TokenErrorResponse toTokenErrorResponse() {
        TokenErrorResponse resp = new TokenErrorResponse();
        resp.setError(errorCode);
        resp.setErrorDescription(errorDescription);
        return resp;
    }
}
