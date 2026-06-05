package cc.nanoic.yunanexus.common.web.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(R r, String tip) {
        super(tip);
        this.code = r.getCode();
    }
}
