package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.data.redis.RedisConnectionFailureException;

@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "cc.nanoic.yunanexus.auth.controller")
public class AuthSecurityExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e) {
        HttpStatus status = resolveHttpStatus(e.getCode());
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            // 基础设施故障：不向客户端泄露细节
            return ResponseEntity.status(status)
                .body(Result.error(e.getCode(), "服务不可用"));
        }
        return ResponseEntity.status(status)
            .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 业务错误码 → HTTP 状态码映射。<p>
     *
     * 约定（docs/auth-security-plan.md §9）：凭证失败 401、授权失败 403、参数/前置条件 400、
     * 注册/申诉冲突 409、基础设施故障 503；未列出的业务码兜底 500。禁止凭证失败返回 403。
     */
    private HttpStatus resolveHttpStatus(int code) {
        // 401 未认证：令牌 / 会话 / 凭证 / 管理员密钥
        if (code == ErrorCode.UNAUTHORIZED.getCode()
            || code == ErrorCode.BAD_CREDENTIALS.getCode()
            || code == ErrorCode.AUTH_USER_NOT_FOUND.getCode()
            || code == ErrorCode.TOKEN_INVALID.getCode()
            || code == ErrorCode.TOKEN_EXPIRED.getCode()
            || code == ErrorCode.TOKEN_REVOKED.getCode()
            || code == ErrorCode.REFRESH_TOKEN_INVALID.getCode()
            || code == ErrorCode.ADMIN_TOKEN_INVALID.getCode()
            || code == ErrorCode.SESSION_NOT_FOUND.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        // 403 无权限：客户端 / 平台授权 / 账号状态 / 管理后台未启用
        if (code == ErrorCode.FORBIDDEN.getCode()
            || code == ErrorCode.CLIENT_DISABLED.getCode()
            || code == ErrorCode.CLIENT_NOT_FOUND.getCode()
            || code == ErrorCode.PLATFORM_AUTH_REVOKED.getCode()
            || code == ErrorCode.ADMIN_DISABLED.getCode()
            || code == ErrorCode.USER_DISABLED.getCode()
            || code == ErrorCode.USER_LOCKED.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        // 404 资源不存在：管理后台目标对象
        if (code == ErrorCode.NOT_FOUND.getCode()
            || code == ErrorCode.APPEAL_NOT_FOUND.getCode()
            || code == ErrorCode.ADMIN_USER_NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        // 409 冲突：注册重复 / 注册处理中 / 申诉重复 / 申诉已处理
        if (code == ErrorCode.REGISTER_USER_ALREADY_EXISTS.getCode()
            || code == ErrorCode.REGISTER_PROCESSING.getCode()
            || code == ErrorCode.APPEAL_ALREADY_PENDING.getCode()
            || code == ErrorCode.APPEAL_ALREADY_AUDITED.getCode()) {
            return HttpStatus.CONFLICT;
        }
        // 400 参数错误 / 前置条件（需验证码、验证码错误）
        if (code == ErrorCode.BAD_REQUEST.getCode()
            || code == ErrorCode.CAPTCHA_REQUIRED.getCode()
            || code == ErrorCode.CAPTCHA_ERROR.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        // 503 基础设施故障
        if (code == ErrorCode.INTERNAL_ERROR.getCode()
            || code == ErrorCode.REGISTER_FAILED.getCode()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), "参数校验失败"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误"));
    }

    @ExceptionHandler({DataAccessException.class, RedisConnectionFailureException.class, IllegalStateException.class})
    public ResponseEntity<Result<Void>> handleInfrastructureException(Exception e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Result.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务不可用"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务不可用"));
    }
}
