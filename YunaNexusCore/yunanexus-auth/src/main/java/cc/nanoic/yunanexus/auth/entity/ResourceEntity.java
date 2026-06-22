package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resources")
public class ResourceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private Integer type; // 0目录 1菜单 2页面资源(按钮/表格列)
    private String icon;
    private String path;
    private String redirect;
    private String component;
    private Integer sortNo;
    private Integer visible; // 0隐藏 1显示
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
