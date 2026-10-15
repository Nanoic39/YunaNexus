package cc.nanoic.yunanexus.permission.service;

import java.util.Map;

/**
 * 鉴权判定服务（资源实例级 + RBAC + ABAC）。
 *
 * @author yunanexus
 */
public interface AuthorizationService {

    /**
     * 判定用户对指定资源 / 动作是否被授权。<p>
     *
     * 判定顺序：<br>
     * 1. ABAC 策略（按优先级降序）：首个「资源 / 动作匹配且条件满足」的策略决定结果；<br>
     * 2. 无策略命中时回退 RBAC（用户 → 启用角色 → 角色权限 → 启用权限）；<br>
     * 3. 均不命中则拒绝。
     *
     * @param userId   全局用户 ID
     * @param resource 资源标识
     * @param action   动作
     * @param context  属性上下文（供 ABAC 条件判定，可为 null）
     * @return 是否允许
     */
    boolean check(long userId, String resource, String action, Map<String, Object> context);

    /**
     * 判定用户对指定资源 / 动作 / 资源实例是否被授权。<p>
     *
     * 判定顺序：<br>
     * 0. 资源实例级授权（L3，仅当给出 {@code resourceType} 与 {@code resourceId} 时）：命中即决定性，
     * 同一实例内 DENY 优先于 ALLOW；<br>
     * 1. ABAC 策略（按优先级降序）；<br>
     * 2. 回退 RBAC；<br>
     * 3. 均不命中则拒绝。
     *
     * @param userId       全局用户 ID
     * @param resource     资源标识
     * @param action       动作
     * @param resourceType 资源类型（L3，可为 null）
     * @param resourceId   资源实例 ID（L3，可为 null）
     * @param context      属性上下文（供 ABAC 条件判定，可为 null）
     * @return 是否允许
     */
    boolean check(long userId, String resource, String action, String resourceType, String resourceId,
                  Map<String, Object> context);
}
