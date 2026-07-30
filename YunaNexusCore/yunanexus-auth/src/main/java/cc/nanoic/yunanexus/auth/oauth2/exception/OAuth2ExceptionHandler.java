package cc.nanoic.yunanexus.auth.oauth2.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * OAuth2 异常全局处理器.
 * <p>捕获 OAuth2Exception 并根据类型返回正确的 RFC 6749 格式响应.</p>
 */
@RestControllerAdvice(basePackages = "cc.nanoic.yunanexus.auth.oauth2")
public class OAuth2ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ExceptionHandler.class);

    @ExceptionHandler(OAuth2Exception.class)
    public Object handleOAuth2Exception(OAuth2Exception e, HttpServletResponse response) throws IOException {
        log.warn("OAuth2Exception: error={}, description={}, httpStatus={}",
                e.getErrorCode(), e.getErrorDescription(), e.getHttpStatus());

        if (e.isRedirectError()) {
            // 授权端点错误 → 302 重定向
            response.sendRedirect(e.buildErrorRedirectUri());
            return null;
        }

        // Token 端点错误 → JSON（由 OAuth2TokenController 中的 catch 块处理）
        // 如果异常穿透到了这里，直接设置 HTTP 状态码并返回 RFC 6749 错误格式
        response.setStatus(e.getHttpStatus());
        return e.toTokenErrorResponse();
    }
}
