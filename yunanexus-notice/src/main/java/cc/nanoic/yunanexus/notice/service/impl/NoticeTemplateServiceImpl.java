package cc.nanoic.yunanexus.notice.service.impl;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.NoticeTemplateSaveRequest;
import cc.nanoic.yunanexus.notice.domain.NoticeTemplateView;
import cc.nanoic.yunanexus.notice.entity.NoticeTemplate;
import cc.nanoic.yunanexus.notice.mapper.NoticeTemplateMapper;
import cc.nanoic.yunanexus.notice.service.NoticeTemplateService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知模板管理服务实现（roadmap 7.5）。
 *
 * @author yunanexus
 */
@Service
public class NoticeTemplateServiceImpl implements NoticeTemplateService {

    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeTemplateMapper templateMapper;

    public NoticeTemplateServiceImpl(NoticeTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @Override
    public NoticeTemplateView create(NoticeTemplateSaveRequest request) {
        if (templateMapper.selectByCode(request.getTemplateCode()) != null) {
            throw new BizException(ErrorCode.NOTICE_TEMPLATE_CODE_EXISTS, request.getTemplateCode());
        }
        NoticeTemplate template = new NoticeTemplate();
        template.setTemplateCode(request.getTemplateCode());
        template.setTemplateName(request.getTemplateName());
        template.setChannel(request.getChannel());
        template.setTitle(request.getTitle());
        template.setContent(request.getContent());
        template.setStatus(NoticeTemplate.STATUS_ENABLED);
        template.setRemark(request.getRemark());
        templateMapper.insert(template);
        return NoticeTemplateView.from(templateMapper.selectByCode(template.getTemplateCode()));
    }

    @Override
    public NoticeTemplateView update(long id, NoticeTemplateSaveRequest request) {
        NoticeTemplate template = requireTemplate(id);
        NoticeTemplate sameCode = templateMapper.selectByCode(request.getTemplateCode());
        if (sameCode != null && !sameCode.getId().equals(id)) {
            throw new BizException(ErrorCode.NOTICE_TEMPLATE_CODE_EXISTS, request.getTemplateCode());
        }
        template.setTemplateCode(request.getTemplateCode());
        template.setTemplateName(request.getTemplateName());
        template.setChannel(request.getChannel());
        template.setTitle(request.getTitle());
        template.setContent(request.getContent());
        template.setRemark(request.getRemark());
        templateMapper.updateById(template);
        return NoticeTemplateView.from(templateMapper.selectById(id));
    }

    @Override
    public void updateStatus(long id, int status) {
        if (status != NoticeTemplate.STATUS_ENABLED && status != NoticeTemplate.STATUS_DISABLED) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法模板状态: " + status);
        }
        NoticeTemplate template = requireTemplate(id);
        template.setStatus(status);
        templateMapper.updateById(template);
    }

    @Override
    public void delete(long id) {
        requireTemplate(id);
        templateMapper.deleteById(id);
    }

    @Override
    public NoticeTemplateView get(long id) {
        return NoticeTemplateView.from(requireTemplate(id));
    }

    @Override
    public PageResult<NoticeTemplateView> list(int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        long total = templateMapper.countAll();
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<NoticeTemplateView> records = templateMapper.selectPage(offset(p, s), s)
                .stream().map(NoticeTemplateView::from).toList();
        return PageResult.of(records, total, p, s);
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int offset(int page, int size) {
        return (page - 1) * size;
    }

    /** 按主键读取模板，不存在抛 {@link ErrorCode#NOTICE_TEMPLATE_NOT_FOUND}。 */
    private NoticeTemplate requireTemplate(long id) {
        NoticeTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.NOTICE_TEMPLATE_NOT_FOUND, "id=" + id);
        }
        return template;
    }
}
