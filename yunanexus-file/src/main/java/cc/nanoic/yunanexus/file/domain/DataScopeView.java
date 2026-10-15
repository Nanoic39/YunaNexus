package cc.nanoic.yunanexus.file.domain;

import lombok.Data;

import java.util.List;

/**
 * 权限中心数据范围解析结果的本地映射（{@code yunanexus-permission} 的
 * {@code DataScopeResponse} 同构）。<p>
 *
 * 文件中心不依赖权限中心模块，故以本地 DTO 承接 Feign 返回值，再转换为
 * {@code DataScopeContext.Scope}。
 *
 * @author yunanexus
 */
@Data
public class DataScopeView {

    /** 全局用户 ID */
    private Long userId;

    /** 是否全量可见 */
    private boolean all;

    /** 可见部门 ID 集合 */
    private List<Long> deptIds;

    /** 非空时追加「仅本人」条件 */
    private Long selfUserId;
}
