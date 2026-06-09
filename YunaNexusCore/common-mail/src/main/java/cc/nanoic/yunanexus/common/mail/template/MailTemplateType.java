package cc.nanoic.yunanexus.common.mail.template;

import lombok.Getter;

@Getter
public enum MailTemplateType {
    // 验证码
    VERIFY_CODE("verify-code", "邮箱验证码"),
    // 注册成功欢迎
    WELCOME("welcome", "注册欢迎"),
    // 系统告警
    SYSTEM_ALERT("system-alert", "安全告警"),
    // 通知
    NOTIFICATION("notification", "系统通知"),
    // 营销/活动通知
    MARKETING("marketing", "营销通知");

    // 模板名称
    private final String templateName;
    // 描述
    private final String description;

    MailTemplateType(String templateName, String description) {
        this.templateName = templateName;
        this.description = description;
    }
}
