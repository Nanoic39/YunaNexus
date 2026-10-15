package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.common.core.model.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int httpStatus, String message) throws IOException {
        int code;
        if (httpStatus == 401) {
            code = cc.nanoic.yunanexus.common.web.constant.ErrorCode.UNAUTHORIZED.getCode();
        } else if (httpStatus == 403) {
            code = cc.nanoic.yunanexus.common.web.constant.ErrorCode.FORBIDDEN.getCode();
        } else {
            code = cc.nanoic.yunanexus.common.web.constant.ErrorCode.INTERNAL_ERROR.getCode();
        }
        write(response, httpStatus, code, message);
    }

    /**
     * 写出错误响应并指定业务错误码。<p>
     *
     * 供过滤器链在安全上下文中返回比 HTTP 状态更精确的业务码（如管理员密钥无效）。
     *
     * @param response   HTTP 响应
     * @param httpStatus HTTP 状态码
     * @param code       业务错误码
     * @param message    错误描述
     */
    public void write(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(code, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
