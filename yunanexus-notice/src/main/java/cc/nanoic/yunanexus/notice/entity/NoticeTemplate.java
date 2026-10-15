package cc.nanoic.yunanexus.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知模板实体（{@code notice_template}）。<p>
 *
 * {@code title} / {@code content} 支持 {@code ${占位符}}，发送时按参数渲染。
 *
 * @author yunanexus
 */
@Data
@TableName("notice_template")
public class NoticeTemplate {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码（全局唯一） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 通道：1-站内信 2-邮件 3-短信 */
    private Integer channel;

    /** 标题模板 */
    private String title;

    /** 内容模板 */
    private String content;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
