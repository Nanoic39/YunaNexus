package cc.nanoic.yunanexus.permission.domain;

import cc.nanoic.yunanexus.permission.entity.PermMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。
 *
 * @author yunanexus
 */
@Data
public class MenuTreeNode {

    private Long id;
    private String menuCode;
    private String menuName;
    private Long parentId;
    private String path;
    private Integer sort;
    private List<MenuTreeNode> children = new ArrayList<>();

    public static MenuTreeNode from(PermMenu menu) {
        MenuTreeNode node = new MenuTreeNode();
        node.setId(menu.getId());
        node.setMenuCode(menu.getMenuCode());
        node.setMenuName(menu.getMenuName());
        node.setParentId(menu.getParentId());
        node.setPath(menu.getPath());
        node.setSort(menu.getSort());
        return node;
    }
}
