package cc.nanoic.yunanexus.notice.domain;

import cc.nanoic.yunanexus.notice.entity.PortalPage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门户页面管理视图（在页面内容基础上附带管理元数据）。<p>
 *
 * {@code sections} 由服务层反序列化后传入，视图本身不依赖 JSON 工具。
 *
 * @author yunanexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalPageView extends PortalPageContent {

    /** 数据库主键（管理端定位用） */
    private Long recordId;

    private Integer sortOrder;

    /** 状态：1-已发布 0-草稿 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static PortalPageView from(PortalPage page, List<PortalSection> sections) {
        PortalPageView view = new PortalPageView();
        view.setRecordId(page.getId());
        view.setId(page.getPageId());
        view.setPath(page.getPath());
        view.setTitle(page.getTitle());
        view.setDescription(page.getDescription());
        view.setNav(page.getNav() != null && page.getNav() == 1);
        view.setSections(sections);
        view.setSortOrder(page.getSortOrder());
        view.setStatus(page.getStatus());
        view.setCreateTime(page.getCreateTime());
        view.setUpdateTime(page.getUpdateTime());
        return view;
    }
}
