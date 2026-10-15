package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.AuthUser;

/**
 * 注册持久化服务（独立事务边界）。<p>
 * <p>
 * 将「写 auth_user + 写注册 outbox」放在同一本地事务内，保证注册成功后必有待同步记录
 * （ADR-021）。拆为独立 Bean 是为了让 {@code @Transactional} 生效（避免自调用绕过代理）。
 *
 * @author yunanexus
 */
public interface RegisterPersistenceService {

    /**
     * 在同一事务内写入 auth_user 与 outbox 记录。
     *
     * @param user 已构造完成的用户实体（密码已 BCrypt、外部 ID 已编码）
     * @return 写入的用户（含自增主键）
     */
    AuthUser persistWithOutbox(AuthUser user);
}
