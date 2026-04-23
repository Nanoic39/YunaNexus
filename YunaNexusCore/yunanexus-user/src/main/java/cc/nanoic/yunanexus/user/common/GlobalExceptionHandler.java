package cc.nanoic.yunanexus.user.common;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获【自定义业务异常】
     * @param e 错误消息
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getRes().getMsg());
        return Result.fail(e.getRes());
    }

    /**
     * 捕获【资源不存在】
     * @param e 错误消息
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNotFoundException(Exception e) {
        log.error("资源不存在：{}", e.getMessage());
        return Result.fail(R.NOT_FOUND);
    }

    /**
     * 捕获【参数校验错误】
     * @param e 错误消息
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误：{}", e.getMessage());
        return Result.fail(R.PARAM_ERROR, e.getMessage());
    }

    /**
     * 兜底捕获【所有系统异常】
     * @param e 错误消息
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleGlobalException(Exception e) {
        log.error("【系统未知异常】", e); // 完整堆栈打印
        return Result.fail(R.SERVER_ERROR, e.getMessage(), R.SERVER_ERROR.getTip());
    }
}
