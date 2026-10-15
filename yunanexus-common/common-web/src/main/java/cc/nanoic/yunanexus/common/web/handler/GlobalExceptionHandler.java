package cc.nanoic.yunanexus.common.web.handler;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j // TODO: 改造为向日志中心推送日志
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：可预期，无需打印堆栈，按 warn 级别记录即可。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage(), e.getTips());
    }

    /**
     * 参数校验异常：@Valid 校验失败时抛出。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldError() == null
                ? "参数校验失败"
                : e.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("Validation failed: {}", detail);
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), detail);
    }

    /**
     * 缺失必填请求参数：映射为 400，而非兜底 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String detail = "缺少必填参数: " + e.getParameterName();
        log.warn("Missing required parameter: {}", e.getParameterName());
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), detail);
    }

    /**
     * 参数类型不匹配：映射为 400，而非兜底 500。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String detail = "参数类型错误: " + e.getName();
        log.warn("Parameter type mismatch: {}", e.getName());
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), detail);
    }

    /**
     * 请求体不可读（JSON 解析失败）：映射为 400，而非兜底 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body not readable: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /**
     * 兜底异常：未预期的错误，控制台打印完整堆栈便于排查。// TODO: 后续改造为向日志中心推送异常日志
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未预期错误", e);
        return Result.error(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getMsg()
        );
    }
}
