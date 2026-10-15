package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.notice.domain.NoticeTemplateSaveRequest;
import cc.nanoic.yunanexus.notice.domain.NoticeTemplateView;
import cc.nanoic.yunanexus.notice.service.NoticeTemplateService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知模板管理接口（roadmap 7.5）。<p>
 *
 * 模板支持 {@code ${占位符}}，发送时按参数渲染；编码全局唯一。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/notice/template")
public class NoticeTemplateController {

    @Resource
    private NoticeTemplateService noticeTemplateService;

    /**
     * 新增模板。
     */
    @PostMapping
    public Result<NoticeTemplateView> create(@Valid @RequestBody NoticeTemplateSaveRequest request) {
        return Result.success(noticeTemplateService.create(request));
    }

    /**
     * 修改模板。
     */
    @PutMapping("/{id}")
    public Result<NoticeTemplateView> update(@PathVariable("id") long id,
                                             @Valid @RequestBody NoticeTemplateSaveRequest request) {
        return Result.success(noticeTemplateService.update(id, request));
    }

    /**
     * 启用 / 停用模板。
     *
     * @param status 1-启用 0-停用
     */
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") long id, @RequestParam("status") int status) {
        noticeTemplateService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 删除模板。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        noticeTemplateService.delete(id);
        return Result.success();
    }

    /**
     * 查询模板详情。
     */
    @GetMapping("/{id}")
    public Result<NoticeTemplateView> get(@PathVariable("id") long id) {
        return Result.success(noticeTemplateService.get(id));
    }

    /**
     * 分页查询模板。
     */
    @GetMapping("/list")
    public Result<PageResult<NoticeTemplateView>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(noticeTemplateService.list(page, size));
    }
}
