package cc.nanoic.yunanexus.auth.service;

/**
 * 登录失败计数与锁定服务（Redis 原子计数 + DB 落库，ADR-003）。<p>
 *
 * 失败计数采用 Redis 原子自增（高并发下不丢计数），达到阈值后锁定账号（DB 落库 +
 * Redis 标记锁定截止时间）。登录成功清零。具体阈值见 {@code AuthProperties}。
 *
 * @author yunanexus
 */
public interface LoginAttemptService {

    /**
     * 记录一次登录失败并返回当前累计失败次数。
     *
     * @param username 账号
     * @return 累计失败次数（本次失败后）
     */
    int recordFailure(String username);

    /**
     * 是否已触发验证码（失败次数 ≥ captchaThreshold）。
     *
     * @param username 账号
     * @return true 需要验证码
     */
    boolean captchaRequired(String username);

    /**
     * 是否已锁定（锁定截止时间未到）。
     *
     * @param username 账号
     * @return true 已锁定
     */
    boolean isLocked(String username);

    /**
     * 锁定账号（DB 落库 + Redis 标记锁定截止时间）。
     *
     * @param username 账号
     * @return 锁定的截止时间（epoch 毫秒）
     */
    long lock(String username);

    /**
     * 登录成功：清零 Redis 失败计数。
     *
     * @param username 账号
     */
    void reset(String username);
}
