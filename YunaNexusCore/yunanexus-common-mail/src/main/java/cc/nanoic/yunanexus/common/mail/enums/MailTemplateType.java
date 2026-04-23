package cc.nanoic.yunanexus.common.mail.enums;

/**
 * 邮件类型
 */
public enum MailTemplateType {
    /**
     * 验证码
     * @param code 验证码
     * @param minutes 过期时间（分钟）
     */
    VERIFICATION,
    /**
     * 通知
     * 
     * @param serviceName 发起通知的服务名称
     * @param title       主题
     * @param content     内容
     * @param actionUrl   具体内容链接
     */
    NOTIFICATION,
    ALERT
}
