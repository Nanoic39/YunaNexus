package cc.nanoic.yunanexus.common.web.common;

import lombok.Data;

@Data
public class Result <T> {
    private int code;
    private String msg;
    private String tip;
    private T data;

    public Result() {}

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 成功：返回响应码、数据、提示
    public static <T> Result<T> success(T data) {
        return new Result<>(R.SUCCESS.getCode(), R.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> success(T data, String msg) {
        return new Result<>(R.SUCCESS.getCode(), msg, data);
    }

    // 返回失败内容
    public static <T> Result<T> fail(R r, String tip) {
        Result<T> result = new Result<>();
        result.code = r.getCode();
        result.msg = r.getMsg();
        result.tip = tip;
        return result;
    }

    public static <T> Result<T> fail(int code, String tip) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = tip;
        result.tip = tip;
        return result;
    }
}
