package cc.nanoic.yunanexus.permission.domain;

import lombok.Data;

import java.util.List;

/**
 * 数据范围解析结果（供业务侧数据权限拦截器注入查询条件）。<p>
 *
 * 语义：{@code all=true} 时不做限制；否则可见条件为
 * 「{@code deptId IN deptIds}」与「{@code createBy = selfUserId}」的<b>并集</b>，
 * 两者都为空表示无可见数据。多个角色取并集（最宽）。
 *
 * @author yunanexus
 */
@Data
public class DataScopeResponse {

    /** 全局用户 ID */
    private Long userId;

    /** 是否全量可见（任一启用角色 data_scope=全部） */
    private boolean all;

    /** 可见部门 ID 集合（已展开「本部门及以下」；空表示无部门范围） */
    private List<Long> deptIds;

    /** 非空时表示追加「仅本人」条件（{@code create_by = selfUserId}） */
    private Long selfUserId;

    public static DataScopeResponse of(long userId, boolean all, List<Long> deptIds, Long selfUserId) {
        DataScopeResponse response = new DataScopeResponse();
        response.setUserId(userId);
        response.setAll(all);
        response.setDeptIds(deptIds == null ? List.of() : deptIds);
        response.setSelfUserId(selfUserId);
        return response;
    }
}
