package cc.nanoic.yunanexus.common.web.exception;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 业务代码中主动抛出，由 GlobalExceptionHandler 统一捕获并转换为
 * Result
 *
 * @author yunanexus
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String tips;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.tips = null;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMsg() + ": " + detail);
        this.code = errorCode.getCode();
        this.tips = null;
    }

    public BizException(ErrorCode errorCode, String detail, String tips) {
        super(errorCode.getMsg() + ": " + detail);
        this.code = errorCode.getCode();
        this.tips = tips;
    }
}