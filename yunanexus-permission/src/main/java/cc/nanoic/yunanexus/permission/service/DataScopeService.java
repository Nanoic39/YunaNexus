package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.DataScopeResponse;

/**
 * 数据范围解析服务（L4 行级数据权限）。
 *
 * @author yunanexus
 */
public interface DataScopeService {

    /**
     * 解析用户的数据范围（多角色取并集 / 最宽）。<p>
     *
     * 无启用角色或无任何数据范围授权时，返回「无可见数据」（{@code all=false}、
     * {@code deptIds} 为空、{@code selfUserId} 为空），业务侧据此拒绝返回数据。
     *
     * @param userId 全局用户 ID
     * @return 数据范围
     */
    DataScopeResponse resolve(long userId);
}
