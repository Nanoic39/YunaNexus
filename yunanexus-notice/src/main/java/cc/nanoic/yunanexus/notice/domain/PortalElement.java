package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户区块内的元素（对应前端 {@code ContentElement}）。
 *
 * @author yunanexus
 */
@Data
public class PortalElement {

    private String id;

    /** 元素类型：hero / text / list / metrics / cards / links / timeline / services / divider */
    private String type;

    private String title;

    private String text;

    /** services 聚合状态地址，配置后优先于条目独立地址 */
    private String statusUrl;

    /** 状态刷新间隔（秒），前端默认 30 秒 */
    private Integer statusIntervalSeconds;

    private List<PortalItem> items = new ArrayList<>();
}
