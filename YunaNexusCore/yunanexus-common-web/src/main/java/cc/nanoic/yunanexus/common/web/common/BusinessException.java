package cc.nanoic.yunanexus.common.web.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final R res;

    public BusinessException(R res) {
        this.res = res;
    }

    public BusinessException(R res, String msg) {
        super(msg);
        this.res = res;
    }
}
