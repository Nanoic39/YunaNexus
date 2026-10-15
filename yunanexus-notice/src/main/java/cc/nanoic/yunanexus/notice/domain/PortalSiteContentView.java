package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户内容根结构（对应前端 {@code SiteContent}）。<p>
 *
 * 公开读接口 {@code GET /api/v1/notice/portal/content} 的输出；
 * {@code pages} 仅包含已发布（status=1）页面，按 {@code sort_order} 升序。
 *
 * @author yunanexus
 */
@Data
public class PortalSiteContentView {

    private PortalSiteMeta site;

    private List<PortalPageContent> pages = new ArrayList<>();
}
