package cc.nanoic.yunanexus.notice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 门户站点保存请求（管理端，整体覆盖站点元信息）。
 *
 * @author yunanexus
 */
@Data
public class PortalSiteSaveRequest {

    @NotBlank(message = "站点名称不能为空")
    @Size(max = 128, message = "站点名称长度不能超过 128")
    private String name;

    @Size(max = 255, message = "标语长度不能超过 255")
    private String tagline;

    @Size(max = 512, message = "简介长度不能超过 512")
    private String description;

    @Size(max = 32, message = "版本号长度不能超过 32")
    private String version;

    @Size(max = 255, message = "仓库地址长度不能超过 255")
    private String repositoryUrl;

    @Size(max = 255, message = "页脚文案长度不能超过 255")
    private String footerNote;
}
