package cc.nanoic.yunanexus.common.web.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private int code;
    private String msg;
    private String tip;
    private Long timestamp;
    private T data;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(R.SUCCESS.getCode());
        result.setMsg(R.SUCCESS.getMsg());
        result.setTip(R.SUCCESS.getTip());
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = success();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(T data, String tip) {
        Result<T> result = success(data);
        result.setTip(tip);
        return result;
    }

    public static <T> Result<T> success(String tip) {
        Result<T> result = success();
        result.setTip(tip);
        return result;
    }

    public static <T> Result<T> fail(R res) {
        Result<T> result = new Result<>();
        result.setCode(res.getCode());
        result.setMsg(res.getMsg());
        result.setTip(res.getTip());
        return result;
    }

    public static <T> Result<T> fail(R res, String tip) {
        Result<T> result = fail(res);
        result.setTip(tip);
        return result;
    }

    public static <T> Result<T> fail(R res, String msg, String tip) {
        Result<T> result = fail(res);
        result.setMsg(msg);
        result.setTip(tip);
        return result;
    }
}
