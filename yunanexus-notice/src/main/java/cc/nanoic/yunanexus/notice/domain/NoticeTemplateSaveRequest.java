package cc.nanoic.yunanexus.notice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通知模板保存请求（新增 / 修改共用）。
 *
 * @author yunanexus
 */
@Data
public class NoticeTemplateSaveRequest {

    /** 模板编码（全局唯一） */
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /** 通道：1-站内信 2-邮件 3-短信 */
    @NotNull(message = "通道不能为空")
    private Integer channel;

    /** 标题模板（支持 ${占位符}） */
    private String title;

    /** 内容模板（支持 ${占位符}） */
    private String content;

    /** 备注 */
    private String remark;
}
