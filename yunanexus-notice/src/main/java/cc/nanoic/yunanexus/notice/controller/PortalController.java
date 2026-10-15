package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.notice.domain.PortalPageContent;
import cc.nanoic.yunanexus.notice.domain.PortalSiteContentView;
import cc.nanoic.yunanexus.notice.service.PortalContentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户内容公开读接口（门户内容持久化）。<p>
 *
 * 面向匿名访问者，网关已放行 {@code /api/v1/notice/portal/content} 与
 * {@code /api/v1/notice/portal/page}；仅返回已发布内容。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/notice/portal")
public class PortalController {

    @Resource
    private PortalContentService portalContentService;

    /**
     * 读取门户完整内容（站点信息 + 全部已发布页面），与前端 {@code SiteContent} 对齐。
     */
    @GetMapping("/content")
    public Result<PortalSiteContentView> content() {
        return Result.success(portalContentService.getPublishedContent());
    }

    /**
     * 按路由路径读取单个已发布页面。
     *
     * @param path 路由路径，如 {@code /}、{@code /about}
     */
    @GetMapping("/page")
    public Result<PortalPageContent> page(@RequestParam("path") String path) {
        return Result.success(portalContentService.getPublishedPage(path));
    }
}
