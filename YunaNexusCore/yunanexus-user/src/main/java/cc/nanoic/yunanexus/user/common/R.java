package cc.nanoic.yunanexus.user.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Timestamp;

@Getter
@AllArgsConstructor
public enum R {
    // 基础
    SUCCESS(200, "操作成功", "操作成功~"),
    SERVER_ERROR(500, "服务器内部异常", "服务异常，请稍后重试"),

    // 客户端错误
    PARAM_ERROR(400, "参数校验失败", "参数错误，请检查输入"),
    NOT_FOUND(404, "资源不存在", "未找到相关数据"),

    // 登录 / Token
    NOT_LOGIN(401, "未登录", "请先登录"),
    NO_PERMISSION(403, "已登录但权限不足", "无权限访问"),

    TOKEN_EXPIRED(4010, "AccessToken过期，RefreshToken有效", ""), // 无感刷新，不提示用户
    REFRESH_TOKEN_EXPIRED(4011, "RefreshToken过期，AccessToken有效", ""), // 正常使用
    TOKEN_ALL_EXPIRED(4012, "双令牌均过期", "登录已过期，请重新登录"),

    // 业务错误
    ACCOUNT_ERROR(1001, "账号或密码错误", "账号或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用", "账号已被禁用，请等待封禁结束或申诉解封后使用!"),
    ;


    private final int code; // 响应码
    private final String msg; // 操作响应
    private final String tip; // 用户可见响应

}
