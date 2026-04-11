package cc.nanoic.yunanexus.user.common;

import lombok.Getter;

/**
 * 自定义业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final R res;

    // 直接使用枚举
    public BusinessException(R res) {
        this.res = res;
    }

    // 枚举+自定义提示
    public BusinessException(R res, String msg) {
        super(msg);
        this.res = res;
    }



}
