package cc.nanoic.yunanexus.common.web.common;

import lombok.Getter;

@Getter
public enum R {
    SUCCESS(200, "操作成功"),
    SERVER_ERROR(500, "服务器内部异常"),
    PARAM_ERROR(400, "参数错误"),
    NOT_LOGIN(401, "用户未登录"),
    NOT_PERMISSION(403, "没有所需权限"),
    LOGIN_ERROR(1000, "登录失败"),
    USER_EXIST(1001, "用户已存在"),
    USER_NOTFOUND(1002, "用户不存在"),
    NOT_FOUND(1003, "未找到信息");


    private final int code;
    private final String msg;

    R(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
