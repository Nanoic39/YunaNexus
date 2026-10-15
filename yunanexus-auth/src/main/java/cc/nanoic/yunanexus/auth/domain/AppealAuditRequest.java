package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申诉审核请求（管理后台）。<p>
 *
 * 处理人默认取管理后台配置，可由请求覆盖以记录实际操作者。
 *
 * @author yunanexus
 */
@Data
public class AppealAuditRequest {

    /** 处理人标识（可选，缺省记为 admin） */
    @Size(max = 64, message = "处理人标识不能超过 64 字")
    private String auditor;

    /** 处理意见 / 备注 */
    @Size(max = 500, message = "处理意见不能超过 500 字")
    private String remark;
}
