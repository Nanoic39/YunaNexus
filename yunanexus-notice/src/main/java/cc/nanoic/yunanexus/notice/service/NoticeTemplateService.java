package cc.nanoic.yunanexus.notice.service;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.notice.domain.NoticeTemplateSaveRequest;
import cc.nanoic.yunanexus.notice.domain.NoticeTemplateView;

/**
 * 通知模板管理服务（roadmap 7.5）。
 *
 * @author yunanexus
 */
public interface NoticeTemplateService {

    /**
     * 新增模板（编码全局唯一，默认启用）。
     *
     * @param request 保存请求
     * @return 模板视图
     */
    NoticeTemplateView create(NoticeTemplateSaveRequest request);

    /**
     * 修改模板。
     *
     * @param id      模板主键
     * @param request 保存请求
     * @return 模板视图
     */
    NoticeTemplateView update(long id, NoticeTemplateSaveRequest request);

    /**
     * 启用 / 停用模板。
     *
     * @param id     模板主键
     * @param status 状态：1-启用 0-停用
     */
    void updateStatus(long id, int status);

    /**
     * 删除模板。
     *
     * @param id 模板主键
     */
    void delete(long id);

    /**
     * 按主键查询模板。
     *
     * @param id 模板主键
     * @return 模板视图
     */
    NoticeTemplateView get(long id);

    /**
     * 分页查询模板。
     *
     * @param page 页码（从 1 起）
     * @param size 每页大小（上限 100）
     * @return 模板分页结果
     */
    PageResult<NoticeTemplateView> list(int page, int size);
}
