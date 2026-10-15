package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

import java.util.List;

/**
 * 门户内容最小条目单位（对应前端 {@code ContentItem}）。<p>
 *
 * 作为 {@link PortalElement#getItems()} 的组成部分以 JSON 落库。
 *
 * @author yunanexus
 */
@Data
public class PortalItem {

    private String id;

    private String title;

    private String text;

    /** metrics 类型的数值 */
    private String value;

    /** metrics 类型的单位 */
    private String unit;

    /** timeline / cards 的状态：done / doing / todo */
    private String status;

    /** services 条目的独立状态查询地址 */
    private String statusUrl;

    /** 跳转链接：以 / 开头为站内路由，http 开头为外部链接 */
    private String link;

    /** cards / list 的标签 */
    private List<String> tags;
}
