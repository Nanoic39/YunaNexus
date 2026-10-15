package cc.nanoic.yunanexus.auth.service;

/**
 * 账号纪元服务（Account Epoch）。<p>
 *
 * 每个账号持有一个 128 位随机值（Base64 URL 无填充 22 字符），永久存储于 Redis。
 * 登录 / 刷新 / SSO 均需校验会话中的 epoch 与当前 epoch 一致；
 * 不一致即视为全局撤销（logout-all）。
 *
 * @author yunanexus
 */
public interface AccountEpochService {

    /**
     * 获取当前纪元，不存在则原子初始化。
     *
     * @param userId 全局用户 ID
     * @return 纪元值（Base64 URL 无填充 22 字符）
     */
    String currentOrCreate(long userId);

    /**
     * 校验给定纪元是否为当前纪元。
     *
     * @param userId 全局用户 ID
     * @param epoch  待校验纪元
     * @return true 表示当前纪元；false 表示 missing 或不匹配
     */
    boolean isCurrent(long userId, String epoch);

    /**
     * 撤销当前纪元（原子覆盖新随机值，旧会话立即失效）。
     *
     * @param userId 全局用户 ID
     */
    void revoke(long userId);
}
