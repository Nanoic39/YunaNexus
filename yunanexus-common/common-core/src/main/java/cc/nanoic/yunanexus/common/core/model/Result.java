package cc.nanoic.yunanexus.common.core.model;

import cc.nanoic.yunanexus.common.core.util.TraceIdHolder;
import lombok.Data;

import java.io.Serializable;

/**
 * 全局统一响应体。<p>
 *
 * code：业务状态码<br>
 * msg：状态描述，用于开发排查<br>
 * tips：面向用户的友好提示，可为null<br>
 * data：业务数据<br>
 * traceId：链路追踪 ID，用于关联日志与定位问题<br>
 * timestamp：响应生成时间戳（毫秒）<br>
 *
 * @author yunanexus
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private String tips;
    private T data;
    private String traceId;
    private long timestamp;

    // 成功

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = "操作成功";
        r.data = data;
        r.traceId = TraceIdHolder.get();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> success(T data, String tips) {
        Result<T> r = success(data);
        r.tips = tips;
        return r;
    }

    // 失败

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        r.traceId = TraceIdHolder.get();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> error(int code, String msg, String tips) {
        Result<T> r = error(code, msg);
        r.tips = tips;
        return r;
    }
}
