package cc.nanoic.yunanexus.notice.domain;

import cc.nanoic.yunanexus.notice.entity.PortalSite;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 门户站点管理视图（在站点元信息基础上附带管理元数据）。
 *
 * @author yunanexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalSiteView extends PortalSiteMeta {

    private String siteKey;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static PortalSiteView from(PortalSite site) {
        PortalSiteView view = new PortalSiteView();
        view.setSiteKey(site.getSiteKey());
        view.setName(site.getName());
        view.setTagline(site.getTagline());
        view.setDescription(site.getDescription());
        view.setVersion(site.getVersion());
        view.setRepositoryUrl(site.getRepositoryUrl());
        view.setFooterNote(site.getFooterNote());
        view.setStatus(site.getStatus());
        view.setCreateTime(site.getCreateTime());
        view.setUpdateTime(site.getUpdateTime());
        return view;
    }
}
