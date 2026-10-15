package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户页面区块（对应前端 {@code ContentSection}）。<p>
 *
 * {@code portal_page.sections} 即以本类型的 JSON 数组落库。
 *
 * @author yunanexus
 */
@Data
public class PortalSection {

    private String id;

    private String title;

    private String description;

    private List<PortalElement> elements = new ArrayList<>();
}
