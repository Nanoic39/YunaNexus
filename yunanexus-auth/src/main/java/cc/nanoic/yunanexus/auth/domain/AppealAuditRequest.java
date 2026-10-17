package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申诉审核请求（管理后台）。<p>
 *
 * 处理人由服务端认证身份决定；兼容保留的 auditor 字段不作为审核身份。
 *
 * @author yunanexus
 */
@Data
public class AppealAuditRequest {

    /** 兼容旧客户端的字段，服务端忽略该值；实际处理人来自认证身份 */
    @Size(max = 64, message = "处理人标识不能超过 64 字")
    private String auditor;

    /** 处理意见 / 备注 */
    @Size(max = 500, message = "处理意见不能超过 500 字")
    private String remark;
}
