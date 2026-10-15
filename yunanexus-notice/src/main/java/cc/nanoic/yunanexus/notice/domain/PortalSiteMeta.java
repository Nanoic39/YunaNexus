package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

/**
 * 站点基本信息（对应前端 {@code SiteMeta}，公开读接口输出结构）。
 *
 * @author yunanexus
 */
@Data
public class PortalSiteMeta {

    private String name;

    private String tagline;

    private String description;

    private String version;

    private String repositoryUrl;

    private String footerNote;
}
