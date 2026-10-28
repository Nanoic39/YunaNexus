package cc.nanoic.yunanexus.common.web.constant;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // ---------- 通用 ----------
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // ---------- 元数据中心 10000 ~ 19999 ----------
    SEGMENT_NOT_FOUND(10001, "号段配置不存在"),
    SEGMENT_LOAD_FAILED(10002, "号段加载失败"),
    SEGMENT_OPTIMISTIC_LOCK_FAILED(10003, "号段领取冲突，请重试"),

    // ---------- 用户中心 20000 ~ 29999 ----------
    USER_NOT_FOUND(20001, "用户不存在"),
    USER_ALREADY_EXISTS(20002, "用户已存在"),
    USER_PASSWORD_ERROR(20003, "密码错误"),
    // 20100~20199 用户标签
    USER_TAG_NOT_FOUND(20101, "用户标签不存在"),
    USER_TAG_CODE_EXISTS(20102, "用户标签编码已存在"),
    // 20200~20299 用户徽章
    USER_BADGE_NOT_FOUND(20201, "用户徽章不存在"),
    USER_BADGE_CODE_EXISTS(20202, "用户徽章编码已存在"),

    // ---------- 认证中心 30000 ~ 39999 ----------
    // 30000~30099 登录相关
    BAD_CREDENTIALS(30001, "账号或密码错误"),
    USER_DISABLED(30002, "账号已禁用"),
    USER_LOCKED(30003, "账号已锁定"),
    CAPTCHA_REQUIRED(30004, "需要验证码"),
    CAPTCHA_ERROR(30005, "验证码错误"),
    AUTH_USER_NOT_FOUND(30006, "账号不存在"),
    CLIENT_NOT_FOUND(30007, "客户端不存在"),
    CLIENT_DISABLED(30008, "客户端已禁用"),
    PLATFORM_AUTH_REVOKED(30009, "平台授权已撤销或停用"),

    // 30100~30199 令牌相关
    TOKEN_INVALID(30101, "令牌无效"),
    TOKEN_EXPIRED(30102, "令牌已过期"),
    TOKEN_REVOKED(30103, "令牌已撤销"),
    REFRESH_TOKEN_INVALID(30104, "刷新令牌无效"),

    // 30200~30299 会话相关
    SESSION_NOT_FOUND(30201, "会话不存在"),

    // 30300~30399 申诉相关
    APPEAL_ALREADY_PENDING(30301, "已有待处理的申诉"),

    // 30400~30499 注册相关
    REGISTER_USER_ALREADY_EXISTS(30401, "账号已存在"),
    REGISTER_PROCESSING(30402, "注册处理中，请稍后重试"),
    REGISTER_FAILED(30403, "注册失败，请稍后重试"),
    // 手机号能力开关关闭时的统一拒绝码（认证中心手机号登录/注册、用户中心手机号绑定共用）
    MOBILE_FEATURE_DISABLED(30404, "手机号功能暂未开放"),

    // 30500~30599 管理后台相关
    ADMIN_TOKEN_INVALID(30500, "管理员密钥无效"),
    ADMIN_DISABLED(30501, "管理后台未启用"),
    APPEAL_NOT_FOUND(30502, "申诉记录不存在"),
    APPEAL_ALREADY_AUDITED(30503, "申诉已处理"),
    ADMIN_USER_NOT_FOUND(30504, "用户不存在"),

    // ---------- 权限中心 40000 ~ 49999 ----------
    // 40000~40099 角色管理
    ROLE_NOT_FOUND(40001, "角色不存在"),
    ROLE_CODE_EXISTS(40002, "角色标识已存在"),
    // 40100~40199 权限管理
    PERMISSION_NOT_FOUND(40101, "权限不存在"),
    PERMISSION_CODE_EXISTS(40102, "权限标识已存在"),
    // 40300~40399 ABAC 策略
    POLICY_NOT_FOUND(40301, "策略不存在"),
    // 40400~40499 组织与数据范围
    DEPT_NOT_FOUND(40401, "部门不存在"),
    DEPT_CODE_EXISTS(40402, "部门编码已存在"),
    DEPT_HAS_CHILDREN(40403, "存在子部门，无法删除"),
    // 40500~40599 资源实例级授权
    GRANT_NOT_FOUND(40501, "资源授权不存在"),
    GRANT_EXISTS(40502, "资源授权已存在"),
    // 40600~40699 字段级权限
    FIELD_RULE_NOT_FOUND(40601, "字段规则不存在"),
    FIELD_RULE_EXISTS(40602, "字段规则已存在"),
    // 40700~40799 菜单/页面资源
    MENU_NOT_FOUND(40701, "菜单不存在"),
    MENU_CODE_EXISTS(40702, "菜单编码已存在"),
    MENU_HAS_CHILDREN(40703, "存在子菜单，无法删除"),

    // ---------- 文件中心 50000 ~ 59999 ----------
    // 50000~50099 上传
    FILE_EMPTY(50001, "上传文件为空"),
    FILE_UPLOAD_FAILED(50002, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(50003, "文件类型不允许"),
    FILE_SIZE_EXCEED(50004, "文件大小超出限制"),
    // 50005~50099 分片上传
    FILE_CHUNK_UPLOAD_FAILED(50005, "分片上传失败"),
    FILE_CHUNK_MISSING(50006, "分片缺失，无法合并"),
    FILE_QUOTA_EXCEEDED(50009, "存储配额不足"),
    FILE_UPLOAD_SESSION_NOT_FOUND(50007, "上传会话不存在或已过期"),
    FILE_CHUNK_INDEX_INVALID(50008, "分片下标非法"),
    // 50100~50199 下载/删除
    FILE_NOT_FOUND(50101, "文件不存在"),
    FILE_DOWNLOAD_FAILED(50102, "文件下载失败"),
    FILE_DELETE_FAILED(50103, "文件删除失败"),
    FILE_ACCESS_DENIED(50104, "无权访问该文件"),
    FILE_RANGE_NOT_SATISFIABLE(50105, "下载范围不合法"),

    // ---------- 通知中心 60000 ~ 69999 ----------
    // 60000~60099 模板管理
    NOTICE_TEMPLATE_NOT_FOUND(60001, "通知模板不存在"),
    NOTICE_TEMPLATE_CODE_EXISTS(60002, "通知模板编码已存在"),
    NOTICE_TEMPLATE_DISABLED(60003, "通知模板已停用"),
    // 60100~60199 发送
    NOTICE_SEND_FAILED(60101, "通知发送失败"),
    NOTICE_CHANNEL_UNSUPPORTED(60102, "通知通道不支持"),
    NOTICE_RECORD_NOT_FOUND(60103, "通知记录不存在"),
    NOTICE_PARAM_MISSING(60104, "通知模板参数缺失"),

    // 60200~60299 门户内容
    PORTAL_SITE_NOT_FOUND(60201, "门户站点配置不存在"),
    PORTAL_PAGE_NOT_FOUND(60202, "门户页面不存在"),
    PORTAL_PAGE_ID_EXISTS(60203, "门户页面标识已存在"),
    PORTAL_PAGE_PATH_EXISTS(60204, "门户页面路径已存在"),
    PORTAL_CONTENT_INVALID(60205, "门户内容格式非法"),

    // ---------- 审计中心 70000 ~ 79999 ----------
    // 70000~70099 日志上报
    AUDIT_REPORT_FAILED(70001, "审计日志上报失败"),
    AUDIT_MESSAGE_INVALID(70002, "审计日志消息格式非法"),
    AUDIT_REPORT_DISABLED(70003, "审计日志采集未启用"),
    AUDIT_MQ_UNAVAILABLE(70004, "审计日志消息队列不可用"),
    // 70100~70199 日志查询
    AUDIT_QUERY_RANGE_INVALID(70101, "审计日志查询时间范围非法"),
    AUDIT_LOG_NOT_FOUND(70102, "审计日志不存在"),
    AUDIT_QUERY_FAILED(70103, "审计日志查询失败"),
    AUDIT_EXPORT_FAILED(70104, "审计日志导出失败"),

    // ---------- 行为分析 80000 ~ 89999 ----------
    // 80000~80099 事件上报
    EVENT_INVALID(80001, "事件格式非法"),
    TRACK_RATE_LIMITED(80002, "上报限流"),
    ANALYTICS_MQ_UNAVAILABLE(80003, "埋点消息队列不可用"),
    // 80100~80199 分析查询
    ANALYTICS_QUERY_INVALID(80101, "分析查询参数非法"),
    ANALYTICS_NOT_READY(80102, "分析结果尚未产出");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
