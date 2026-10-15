package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户页面内容（对应前端 {@code ContentPage}，公开读接口输出结构）。
 *
 * @author yunanexus
 */
@Data
public class PortalPageContent {

    /** 页面标识（前端路由 key，对应 {@code portal_page.page_id}） */
    private String id;

    /** 路由路径，如 /、/about */
    private String path;

    /** 页面标题 */
    private String title;

    private String description;

    /** 是否在门户顶部导航中展示 */
    private Boolean nav;

    private List<PortalSection> sections = new ArrayList<>();
}
