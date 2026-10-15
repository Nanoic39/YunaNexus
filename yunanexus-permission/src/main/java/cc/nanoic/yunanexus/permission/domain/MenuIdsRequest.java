package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 菜单集合设置请求（覆盖式，用于角色菜单绑定）。
 *
 * @author yunanexus
 */
@Data
public class MenuIdsRequest {

    /** 目标菜单 ID 集合（覆盖语义） */
    @NotNull(message = "menuIds 不能为空")
    private List<Long> menuIds;
}
