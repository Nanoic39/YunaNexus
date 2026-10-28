package cc.nanoic.yunanexus.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知记录实体（{@code notice_record}）。<p>
 *
 * 一条记录对应「单接收方 + 单通道」的一次通知；{@code notice_id} 为全局通知 ID
 * （号段服务分配），{@code receiver} 依通道不同为站内信用户 ID / 邮箱 / 手机号。
 *
 * @author yunanexus
 */
@Data
@TableName("notice_record")
public class NoticeRecord {

    /** 状态：待发送 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已发送 */
    public static final int STATUS_SENT = 1;
    /** 状态：发送失败 */
    public static final int STATUS_FAILED = 2;
    /** 状态：已读（站内信） */
    public static final int STATUS_READ = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局通知 ID */
    private Long noticeId;

    /** 模板编码 */
    private String templateCode;

    /** 通道：1-站内信 2-邮件 3-短信 */
    private Integer channel;

    /** 接收方：站内信为用户 ID，邮件为邮箱，短信为手机号 */
    private String receiver;

    /** 渲染后标题 */
    private String title;

    /** 渲染后内容 */
    private String content;

    /** 模板参数（JSON） */
    private String params;

    /** 状态：0-待发送 1-已发送 2-发送失败 3-已读 */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近一次失败原因 */
    private String failReason;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryTime;

    /** 是否已进入死信，不再重试 */
    private Boolean deadLetter;

    /** 发送成功时间 */
    private LocalDateTime sendTime;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
