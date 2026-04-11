package cc.nanoic.yunanexus.user.common;

import lombok.Data;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 全局统一返回结果
 * @param <T> 数据泛型
 */
@Data
public class Result<T> implements Serializable {
    private int code; // 响应码
    private String msg; // 操作反馈消息(仅控制台输出用于Debug)
    private String tip; // 用户可见提示
    private Timestamp timestamp; // 时间戳
    private T data; // 返回数据

    private Result() {
        this.timestamp = new Timestamp(System.currentTimeMillis()); // 构造函数，封装时自动生成时间戳
    }

    // ======================================== 成功返回 ========================================

    /**
     * 默认无返回值成功
     * @return 固定结果 SUCCESS(200, "操作成功", "操作成功~")
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(R.SUCCESS.getCode());
        result.setMsg(R.SUCCESS.getMsg());
        result.setTip(R.SUCCESS.getTip());
        return result;
    }

    /**
     * 带返回值的成功
     * @param data 返回值
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = success();
        result.setData(data);
        return result;
    }


    /**
     * 自定义用户提示文本的带返回值成功
     * @param data 返回值
     * @param tip 自定义提示文本
     */
    public static <T> Result<T> success(T data, String tip) {
        Result<T> result = success(data);
        result.setTip(tip);
        return result;
    }

    /**
     * 自定义提示文本的无返回值成功输出
     * @param tip 自定义用户提示文本
     */
    public static <T> Result<T> success(String tip) {
        Result<T> result = success();
        result.setTip(tip);
        return result;
    }

    // ======================================== 失败返回 ========================================

    /**
     * 枚举失败返回
     * @param res 枚举内容
     */
    public static <T> Result<T> fail(R res) {
        Result<T> result = new Result<>();
        result.setCode(res.getCode());
        result.setMsg(res.getMsg());
        result.setTip(res.getTip());
        return result;
    }

    /**
     * 自定义用户提示
     * @param res 枚举内容
     * @param tip 自定义提示
     */
    public static <T> Result<T> fail(R res, String tip) {
        Result<T> result = fail(res);
        result.setTip(tip);
        return result;
    }

    /**
     * 自定义日志信息 + 用户提示
     * @param res 枚举内容
     * @param msg 自定义日志消息
     * @param tip 自定义用户提示
     */
    public static <T> Result<T> fail(R res, String msg, String tip) {
        Result<T> result = fail(res);
        result.setMsg(msg);
        result.setTip(tip);
        return result;
    }

}
