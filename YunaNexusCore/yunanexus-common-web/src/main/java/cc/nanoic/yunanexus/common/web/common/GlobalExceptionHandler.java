package cc.nanoic.yunanexus.common.web.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        // TODO: 后续需要对接YunaNexusMonitor
        log.error("业务异常：{}", e.getRes().getMsg());
        return Result.fail(e.getRes());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNotFoundException(Exception e) {
        log.error("资源不存在：{}", e.getMessage());
        return Result.fail(R.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误：{}", e.getMessage());
        return Result.fail(R.PARAM_ERROR, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleGlobalException(Exception e) {
        log.error("系统未知异常", e);
        return Result.fail(R.SERVER_ERROR, e.getMessage(), R.SERVER_ERROR.getTip());
    }
}
