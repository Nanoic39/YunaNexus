package cc.nanoic.yunanexus.notice.domain;

/**
 * 通知通道枚举（与 {@code notice_template.channel} / {@code notice_record.channel} 对应）。
 *
 * @author yunanexus
 */
public enum NoticeChannel {

    /** 站内信 */
    INNER(1, "站内信"),
    /** 邮件 */
    EMAIL(2, "邮件"),
    /** 短信（预留） */
    SMS(3, "短信");

    private final int code;
    private final String label;

    NoticeChannel(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按编码解析通道。
     *
     * @param code 通道编码
     * @return 匹配的通道，未知返回 {@code null}
     */
    public static NoticeChannel of(Integer code) {
        if (code == null) {
            return null;
        }
        for (NoticeChannel channel : values()) {
            if (channel.code == code) {
                return channel;
            }
        }
        return null;
    }
}
