package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.notice.domain.PortalPageSaveRequest;
import cc.nanoic.yunanexus.notice.domain.PortalPageView;
import cc.nanoic.yunanexus.notice.domain.PortalSiteSaveRequest;
import cc.nanoic.yunanexus.notice.domain.PortalSiteView;
import cc.nanoic.yunanexus.notice.service.PortalContentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门户内容管理接口（门户内容持久化）。<p>
 *
 * 管理端维护站点信息与页面（含草稿）；网关未放行 {@code /api/v1/notice/portal/admin/**}，
 * 需携带有效登录态访问。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/notice/portal/admin")
public class PortalAdminController {

    @Resource
    private PortalContentService portalContentService;

    /**
     * 查询默认站点配置。
     */
    @GetMapping("/site")
    public Result<PortalSiteView> getSite() {
        return Result.success(portalContentService.getSite());
    }

    /**
     * 保存默认站点配置（整体覆盖）。
     */
    @PutMapping("/site")
    public Result<PortalSiteView> saveSite(@Valid @RequestBody PortalSiteSaveRequest request) {
        return Result.success(portalContentService.saveSite(request));
    }

    /**
     * 查询全部页面（含草稿，按排序号升序）。
     */
    @GetMapping("/pages")
    public Result<List<PortalPageView>> listPages() {
        return Result.success(portalContentService.listPages());
    }

    /**
     * 查询页面详情。
     */
    @GetMapping("/pages/{id}")
    public Result<PortalPageView> getPage(@PathVariable("id") long id) {
        return Result.success(portalContentService.getPage(id));
    }

    /**
     * 新增页面。
     */
    @PostMapping("/pages")
    public Result<PortalPageView> createPage(@Valid @RequestBody PortalPageSaveRequest request) {
        return Result.success(portalContentService.createPage(request));
    }

    /**
     * 修改页面。
     */
    @PutMapping("/pages/{id}")
    public Result<PortalPageView> updatePage(@PathVariable("id") long id,
                                             @Valid @RequestBody PortalPageSaveRequest request) {
        return Result.success(portalContentService.updatePage(id, request));
    }

    /**
     * 删除页面。
     */
    @DeleteMapping("/pages/{id}")
    public Result<Void> deletePage(@PathVariable("id") long id) {
        portalContentService.deletePage(id);
        return Result.success();
    }
}
