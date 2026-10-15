package cc.nanoic.yunanexus.notice.service;

import cc.nanoic.yunanexus.notice.domain.PortalPageContent;
import cc.nanoic.yunanexus.notice.domain.PortalPageSaveRequest;
import cc.nanoic.yunanexus.notice.domain.PortalPageView;
import cc.nanoic.yunanexus.notice.domain.PortalSiteContentView;
import cc.nanoic.yunanexus.notice.domain.PortalSiteSaveRequest;
import cc.nanoic.yunanexus.notice.domain.PortalSiteView;

import java.util.List;

/**
 * 门户内容持久化服务（挂载于通知中心）。<p>
 *
 * 站点级元信息（{@code portal_site}）+ 页面级内容（{@code portal_page}，sections 以 JSON 落库），
 * 对齐前端 {@code SiteContent}（site + pages）契约。<br>
 * 公开读接口仅返回已发布页面；管理端接口提供站点与页面的完整 CRUD。
 *
 * @author yunanexus
 */
public interface PortalContentService {

    /**
     * 公开：聚合读取默认站点已发布内容（site + 已发布 pages，按 sortOrder 升序）。
     *
     * @return 门户内容根结构
     */
    PortalSiteContentView getPublishedContent();

    /**
     * 公开：按路由路径读取已发布页面。
     *
     * @param path 路由路径（如 /、/about）
     * @return 页面内容
     */
    PortalPageContent getPublishedPage(String path);

    /**
     * 管理端：查询默认站点配置。
     *
     * @return 站点视图
     */
    PortalSiteView getSite();

    /**
     * 管理端：保存（upsert）默认站点配置。
     *
     * @param request 保存请求
     * @return 站点视图
     */
    PortalSiteView saveSite(PortalSiteSaveRequest request);

    /**
     * 管理端：查询全部页面（含草稿，按 sortOrder 升序）。
     *
     * @return 页面视图列表
     */
    List<PortalPageView> listPages();

    /**
     * 管理端：按主键查询页面。
     *
     * @param recordId 数据库主键
     * @return 页面视图
     */
    PortalPageView getPage(long recordId);

    /**
     * 管理端：新增页面（页面标识 / 路径站点内唯一）。
     *
     * @param request 保存请求
     * @return 页面视图
     */
    PortalPageView createPage(PortalPageSaveRequest request);

    /**
     * 管理端：修改页面。
     *
     * @param recordId 数据库主键
     * @param request  保存请求
     * @return 页面视图
     */
    PortalPageView updatePage(long recordId, PortalPageSaveRequest request);

    /**
     * 管理端：删除页面。
     *
     * @param recordId 数据库主键
     */
    void deletePage(long recordId);
}
