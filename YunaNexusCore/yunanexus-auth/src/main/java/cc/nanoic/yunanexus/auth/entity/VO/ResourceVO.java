package cc.nanoic.yunanexus.auth.entity.VO;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResourceVO {
    private Long id;
    private String name;
    private String code;
    private Integer type;
    private String icon;
    private String path;
    private String redirect;
    private String component;
    private Integer sortNo;
    private List<ResourceVO> children;

    public ResourceVO() {
        this.children = new ArrayList<>();
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}
