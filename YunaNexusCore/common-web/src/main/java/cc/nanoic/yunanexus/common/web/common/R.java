package cc.nanoic.yunanexus.common.web.common;


public enum R {
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    NOT_LOGIN(401, "用户未登录"),
    NOT_PERMISSION(403, "没有所需权限"),
    LOGIN_ERROR(1000, "登录失败"),
    USER_EXIST(1001, "用户已存在");


    private final int code;
    private final String msg;

    R(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
}
