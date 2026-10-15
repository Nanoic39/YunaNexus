package cc.nanoic.yunanexus.gateway.security;

/**
 * 网关鉴权异常：携带 HTTP 状态码与业务错误码，由过滤器映射为统一响应。
 *
 * @author yunanexus
 */
public class GatewayAuthException extends RuntimeException {

    private final int httpStatus;
    private final int code;

    public GatewayAuthException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }
}
