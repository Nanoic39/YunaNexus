package cc.nanoic.yunanexus.notice.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.PortalPageContent;
import cc.nanoic.yunanexus.notice.domain.PortalPageSaveRequest;
import cc.nanoic.yunanexus.notice.domain.PortalPageView;
import cc.nanoic.yunanexus.notice.domain.PortalSection;
import cc.nanoic.yunanexus.notice.domain.PortalSiteContentView;
import cc.nanoic.yunanexus.notice.domain.PortalSiteMeta;
import cc.nanoic.yunanexus.notice.domain.PortalSiteSaveRequest;
import cc.nanoic.yunanexus.notice.domain.PortalSiteView;
import cc.nanoic.yunanexus.notice.entity.PortalPage;
import cc.nanoic.yunanexus.notice.entity.PortalSite;
import cc.nanoic.yunanexus.notice.mapper.PortalPageMapper;
import cc.nanoic.yunanexus.notice.mapper.PortalSiteMapper;
import cc.nanoic.yunanexus.notice.service.PortalContentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 门户内容持久化服务实现（挂载于通知中心）。<p>
 *
 * 站点元信息存 {@code portal_site}，页面（sections→elements→items）序列化为 JSON 存
 * {@code portal_page}；公开读仅输出已发布页面，管理端提供完整 CRUD。
 *
 * @author yunanexus
 */
@Service
public class PortalContentServiceImpl implements PortalContentService {

    /** 当前仅维护默认站点 */
    private static final String SITE_KEY = PortalSite.DEFAULT_SITE_KEY;

    private final PortalSiteMapper siteMapper;
    private final PortalPageMapper pageMapper;
    private final ObjectMapper objectMapper;

    public PortalContentServiceImpl(PortalSiteMapper siteMapper,
                                    PortalPageMapper pageMapper,
                                    ObjectMapper objectMapper) {
        this.siteMapper = siteMapper;
        this.pageMapper = pageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PortalSiteContentView getPublishedContent() {
        PortalSite site = requireSite();
        List<PortalPageContent> pages = pageMapper.selectList(
                        Wrappers.<PortalPage>lambdaQuery()
                                .eq(PortalPage::getSiteKey, SITE_KEY)
                                .eq(PortalPage::getStatus, PortalPage.STATUS_PUBLISHED)
                                .orderByAsc(PortalPage::getSortOrder)
                                .orderByAsc(PortalPage::getId))
                .stream().map(this::toContent).toList();

        PortalSiteContentView content = new PortalSiteContentView();
        content.setSite(toSiteMeta(site));
        content.setPages(pages);
        return content;
    }

    @Override
    public PortalPageContent getPublishedPage(String path) {
        PortalPage page = pageMapper.selectByPath(SITE_KEY, normalizePath(path));
        if (page == null || page.getStatus() == null || page.getStatus() != PortalPage.STATUS_PUBLISHED) {
            throw new BizException(ErrorCode.PORTAL_PAGE_NOT_FOUND, "path=" + path);
        }
        return toContent(page);
    }

    @Override
    public PortalSiteView getSite() {
        return PortalSiteView.from(requireSite());
    }

    @Override
    public PortalSiteView saveSite(PortalSiteSaveRequest request) {
        PortalSite site = siteMapper.selectBySiteKey(SITE_KEY);
        if (site == null) {
            site = new PortalSite();
            site.setSiteKey(SITE_KEY);
            site.setStatus(PortalSite.STATUS_ENABLED);
            applySite(site, request);
            siteMapper.insert(site);
        } else {
            applySite(site, request);
            siteMapper.updateById(site);
        }
        return PortalSiteView.from(siteMapper.selectBySiteKey(SITE_KEY));
    }

    @Override
    public List<PortalPageView> listPages() {
        return pageMapper.selectList(
                        Wrappers.<PortalPage>lambdaQuery()
                                .eq(PortalPage::getSiteKey, SITE_KEY)
                                .orderByAsc(PortalPage::getSortOrder)
                                .orderByAsc(PortalPage::getId))
                .stream().map(this::toView).toList();
    }

    @Override
    public PortalPageView getPage(long recordId) {
        return toView(requirePage(recordId));
    }

    @Override
    public PortalPageView createPage(PortalPageSaveRequest request) {
        String path = normalizePath(request.getPath());
        if (pageMapper.selectByPageId(SITE_KEY, request.getId()) != null) {
            throw new BizException(ErrorCode.PORTAL_PAGE_ID_EXISTS, request.getId());
        }
        if (pageMapper.selectByPath(SITE_KEY, path) != null) {
            throw new BizException(ErrorCode.PORTAL_PAGE_PATH_EXISTS, path);
        }
        PortalPage page = new PortalPage();
        page.setSiteKey(SITE_KEY);
        page.setPageId(request.getId());
        page.setPath(path);
        applyPage(page, request);
        pageMapper.insert(page);
        return toView(requirePage(page.getId()));
    }

    @Override
    public PortalPageView updatePage(long recordId, PortalPageSaveRequest request) {
        PortalPage page = requirePage(recordId);
        String path = normalizePath(request.getPath());
        PortalPage sameId = pageMapper.selectByPageId(SITE_KEY, request.getId());
        if (sameId != null && !sameId.getId().equals(recordId)) {
            throw new BizException(ErrorCode.PORTAL_PAGE_ID_EXISTS, request.getId());
        }
        PortalPage samePath = pageMapper.selectByPath(SITE_KEY, path);
        if (samePath != null && !samePath.getId().equals(recordId)) {
            throw new BizException(ErrorCode.PORTAL_PAGE_PATH_EXISTS, path);
        }
        page.setPageId(request.getId());
        page.setPath(path);
        applyPage(page, request);
        pageMapper.updateById(page);
        return toView(requirePage(recordId));
    }

    @Override
    public void deletePage(long recordId) {
        requirePage(recordId);
        pageMapper.deleteById(recordId);
    }

    /** 覆盖站点元信息字段。 */
    private static void applySite(PortalSite site, PortalSiteSaveRequest request) {
        site.setName(request.getName());
        site.setTagline(request.getTagline());
        site.setDescription(request.getDescription());
        site.setVersion(request.getVersion());
        site.setRepositoryUrl(request.getRepositoryUrl());
        site.setFooterNote(request.getFooterNote());
    }

    /** 覆盖页面内容字段（sections 序列化为 JSON）。 */
    private void applyPage(PortalPage page, PortalPageSaveRequest request) {
        page.setTitle(request.getTitle());
        page.setDescription(request.getDescription());
        page.setNav(Boolean.TRUE.equals(request.getNav()) ? PortalPage.NAV_TRUE : PortalPage.NAV_FALSE);
        page.setSections(toJson(request.getSections()));
        page.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        page.setStatus(request.getStatus() == null ? PortalPage.STATUS_PUBLISHED : request.getStatus());
    }

    /** 实体 → 公开页面内容。 */
    private PortalPageContent toContent(PortalPage page) {
        PortalPageContent content = new PortalPageContent();
        content.setId(page.getPageId());
        content.setPath(page.getPath());
        content.setTitle(page.getTitle());
        content.setDescription(page.getDescription());
        content.setNav(page.getNav() != null && page.getNav() == PortalPage.NAV_TRUE);
        content.setSections(parseSections(page.getSections()));
        return content;
    }

    /** 实体 → 管理端页面视图。 */
    private PortalPageView toView(PortalPage page) {
        return PortalPageView.from(page, parseSections(page.getSections()));
    }

    /** 站点实体 → 站点元信息。 */
    private static PortalSiteMeta toSiteMeta(PortalSite site) {
        PortalSiteMeta meta = new PortalSiteMeta();
        meta.setName(site.getName());
        meta.setTagline(site.getTagline());
        meta.setDescription(site.getDescription());
        meta.setVersion(site.getVersion());
        meta.setRepositoryUrl(site.getRepositoryUrl());
        meta.setFooterNote(site.getFooterNote());
        return meta;
    }

    /** 读取默认站点，不存在抛 {@link ErrorCode#PORTAL_SITE_NOT_FOUND}。 */
    private PortalSite requireSite() {
        PortalSite site = siteMapper.selectBySiteKey(SITE_KEY);
        if (site == null) {
            throw new BizException(ErrorCode.PORTAL_SITE_NOT_FOUND, "siteKey=" + SITE_KEY);
        }
        return site;
    }

    /** 按主键读取页面，不存在抛 {@link ErrorCode#PORTAL_PAGE_NOT_FOUND}。 */
    private PortalPage requirePage(long recordId) {
        PortalPage page = pageMapper.selectById(recordId);
        if (page == null) {
            throw new BizException(ErrorCode.PORTAL_PAGE_NOT_FOUND, "id=" + recordId);
        }
        return page;
    }

    /** 归一化路由路径：补前导斜杠、去尾部斜杠（与前端 store.normalizePath 一致）。 */
    private static String normalizePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "/";
        }
        String withSlash = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return withSlash.length() > 1 ? withSlash.replaceAll("/+$", "") : "/";
    }

    /** 序列化页面区块为 JSON。 */
    private String toJson(List<PortalSection> sections) {
        try {
            return objectMapper.writeValueAsString(sections == null ? List.of() : sections);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PORTAL_CONTENT_INVALID, "序列化页面区块失败: " + e.getMessage());
        }
    }

    /** 反序列化页面区块 JSON，空值返回空列表。 */
    private List<PortalSection> parseSections(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            PortalSection[] sections = objectMapper.readValue(json, PortalSection[].class);
            return new ArrayList<>(Arrays.asList(sections));
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PORTAL_CONTENT_INVALID, "解析页面区块失败: " + e.getMessage());
        }
    }
}
