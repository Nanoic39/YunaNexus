package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增 / 更新请求（Phase D）。
 *
 * @author yunanexus
 */
@Data
public class MenuSaveRequest {

    /** 菜单编码（唯一） */
    @NotBlank(message = "menuCode 不能为空")
    @Size(max = 64, message = "menuCode 长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z0-9_.:-]+$", message = "menuCode 仅支持字母、数字、下划线、点、冒号、中划线")
    private String menuCode;

    /** 菜单名称 */
    @NotBlank(message = "menuName 不能为空")
    @Size(max = 128, message = "menuName 长度不能超过 128")
    private String menuName;

    /** 父菜单 ID（0=根；更新时忽略） */
    @Min(0)
    private Long parentId;

    /** 前端路由路径 */
    @Size(max = 255, message = "path 长度不能超过 255")
    private String path;

    /** 排序（升序，为空默认 0） */
    private Integer sort;

    /** 状态：1-启用 0-禁用（为空时新增默认启用） */
    @Min(0)
    @Max(1)
    private Integer status;
}
