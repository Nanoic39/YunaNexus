package cc.nanoic.yunanexus.common.web.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

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

    TOKEN_EXPIRED(4010, "AccessToken过期，RefreshToken有效", ""),
    REFRESH_TOKEN_EXPIRED(4011, "RefreshToken过期，AccessToken有效", ""),
    TOKEN_ALL_EXPIRED(4012, "双令牌均过期", "登录已过期，请重新登录"),

    // 业务错误 - 账户相关
    ACCOUNT_ERROR(1001, "账号或密码错误", "账号或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用", "账号已被禁用，请等待封禁结束或申诉解封后使用!"),
    ACCOUNT_EXISTS(1003, "当前账户已存在", "当前用户名已注册"),
    EMAIL_EXISTS(1004, "当前邮箱已存在", "当前邮箱已被注册"),
    ACCOUNT_DELETE(1005, "账号已注销", "账号已注销!"),

    // 业务错误 - 请求频率相关
    REQ_API_LIMIT(2001, "当前接口请求频率过快", "请求频率过快，请稍后重试"),
    REQ_GLOBAL_LIMIT(2002, "全局请求频率过快", "全局请求频率过快，请稍后重试");

    private final int code;
    private final String msg;
    private final String tip;
}