package cc.nanoic.yunanexus.auth.config;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Redis key 构建器（防用户输入注入 hash tag）。<p>
 *
 * 统一 key 前缀与命名空间，确保 sid/userId 合法。
 * session/revoked 使用相同 hash tag {sid} 确保同一 slot，userSessions 使用 {u-userId}
 * 避免与 session key 共享同一 hash tag（用户输入不得影响 slot 分布）。
 * accountEpoch 使用 {u-userId}，ssoRoot/ssoCode 使用 {hash} 确保同一 slot。
 *
 * @author yunanexus
 */
@Component
public class AuthRedisKeys {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[A-Za-z0-9:_-]{1,128}");
    private static final Pattern SID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern HASH_PATTERN = Pattern.compile("[a-f0-9]{64}");

    private final String prefix;

    public AuthRedisKeys(AuthProperties properties) {
        String prefix = properties.getRedisKeyPrefix();
        if (prefix == null || !NAMESPACE_PATTERN.matcher(prefix).matches()) {
            throw new IllegalArgumentException("redisKeyPrefix 不合法: " + prefix);
        }
        this.prefix = prefix;
    }

    /**
     * 会话 key（TTL 与会话过期时间一致）。
     */
    public String session(String sid) {
        validateSid(sid);
        return prefix + ":{" + sid + "}:session:v2";
    }

    /**
     * 撤销集 key（ZSET member=jti, score=绝对过期毫秒）。
     */
    public String revoked(String sid) {
        validateSid(sid);
        return prefix + ":{" + sid + "}:revoked:v2";
    }

    /**
     * 用户会话反向索引 key（ZSET member=sid, score=会话过期毫秒）。
     */
    public String userSessions(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 必须 > 0");
        }
        return prefix + ":user_sessions:{u-" + userId + "}:v2";
    }

    /**
     * 账号纪元 key（永久无 TTL）。
     */
    public String accountEpoch(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 必须 > 0");
        }
        return prefix + ":account_epoch:{u-" + userId + "}:v1";
    }

    /**
     * SSO root key（TTL ≤ 源会话过期时间）。
     *
     * @param hash SHA-256 十六进制小写（严格 64 位）
     */
    public String ssoRoot(String hash) {
        validateHash(hash);
        return prefix + ":sso_root:{" + hash + "}:v1";
    }

    /**
     * SSO code key（TTL ≤ 60 秒）。
     *
     * @param hash SHA-256 十六进制小写（严格 64 位）
     */
    public String ssoCode(String hash) {
        validateHash(hash);
        return prefix + ":sso_code:{" + hash + "}:v1";
    }

    private void validateSid(String sid) {
        if (sid == null || !SID_PATTERN.matcher(sid).matches()) {
            throw new IllegalArgumentException("sid 不合法: " + sid);
        }
    }

    private void validateHash(String hash) {
        if (hash == null || !HASH_PATTERN.matcher(hash).matches()) {
            throw new IllegalArgumentException("hash 必须为 64 位小写十六进制");
        }
    }
}
