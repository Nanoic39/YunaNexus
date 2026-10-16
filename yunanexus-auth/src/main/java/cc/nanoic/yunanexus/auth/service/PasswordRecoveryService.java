package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.client.PasswordMailClient;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** 邮箱验证、有限次数的一次性凭证与密码审计；管理员无主动重置入口。 */
@Service
public class PasswordRecoveryService {
    private final AuthUserMapper users;
    private final StringRedisTemplate redis;
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final AccountEpochService epochs;
    private final SessionService sessions;
    private final PasswordMailClient mail;

    public PasswordRecoveryService(
            AuthUserMapper users,
            StringRedisTemplate redis,
            JdbcTemplate db,
            PasswordEncoder encoder,
            AccountEpochService epochs,
            SessionService sessions,
            PasswordMailClient mail) {
        this.users = users;
        this.redis = redis;
        this.db = db;
        this.encoder = encoder;
        this.epochs = epochs;
        this.sessions = sessions;
        this.mail = mail;
    }

    @org.springframework.beans.factory.annotation.Autowired private LoginAttemptService attempts;

    private void invalidate(long uid) {
        epochs.revoke(uid);
        sessions.kickAll(uid);
        db.update("UPDATE auth_personal_api_key SET revoked=1 WHERE user_id=?", uid);
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support
                                    .TransactionSynchronization() {
                                public void afterCommit() {
                                    epochs.revoke(uid);
                                    sessions.kickAll(uid);
                                }
                            });
    }

    private static final DefaultRedisScript<Long> LIMIT =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]);if n==1 then"
                            + " redis.call('EXPIRE',KEYS[1],ARGV[1]) end;return n",
                    Long.class);
    private static final DefaultRedisScript<String> VERIFY =
            new DefaultRedisScript<>(
                    "local v=redis.call('GET',KEYS[1]);if not v then return nil end;local"
                        + " n=redis.call('INCR',KEYS[2]);if n==1 then"
                        + " redis.call('EXPIRE',KEYS[2],600) end;if n>5 then"
                        + " redis.call('DEL',KEYS[1]);return nil end;local"
                        + " p=string.find(v,'|',1,true);if string.sub(v,1,p-1)~=ARGV[1] then return"
                        + " nil end;redis.call('DEL',KEYS[1],KEYS[2]);return string.sub(v,p+1)",
                    String.class);
    private static final DefaultRedisScript<String> TAKE =
            new DefaultRedisScript<>(
                    "local v=redis.call('GET',KEYS[1]);if v then redis.call('DEL',KEYS[1])"
                            + " end;return v",
                    String.class);

    private void limit(String key, int max, int seconds) {
        Long n = redis.execute(LIMIT, List.of(key), Integer.toString(seconds));
        if (n == null || n > max) throw bad("请求频繁，请稍后重试");
    }

    private static String email(String v) {
        if (v == null || v.length() > 254 || !v.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw bad("请输入有效邮箱");
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private String key(String name, String email) {
        if (name == null || name.isBlank() || name.length() > 64) throw bad("请输入有效用户名");
        return "auth:password-reset:"
                + OAuthApplicationService.hash(name.trim().toLowerCase(Locale.ROOT) + "|" + email);
    }

    public void request(String username, String address, String ip) {
        String email = email(address), key = key(username, email);
        limit("auth:reset:ip:" + OAuthApplicationService.hash(ip), 20, 600);
        limit("auth:reset:day:" + OAuthApplicationService.hash(email), 10, 86400);
        if (!Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key + ":cooldown", "1", Duration.ofSeconds(60))))
            throw bad("请60秒后再发送验证码");
        var user = users.selectByUsername(username.trim());
        if (user == null
                || user.getEmail() == null
                || !email.equalsIgnoreCase(user.getEmail())
                || Integer.valueOf(0).equals(user.getStatus())) return;
        String code = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000));
        redis.opsForValue()
                .set(
                        key,
                        OAuthApplicationService.hash(code) + "|" + user.getUserId(),
                        Duration.ofMinutes(10));
        redis.delete(key + ":attempts");
        try {
            var r = mail.send(new PasswordMailClient.Mail(email, code));
            if (r == null || r.getCode() != 200) redis.delete(key);
        } catch (Exception e) {
            redis.delete(key);
        }
    }

    @Transactional
    public String verify(String username, String address, String code) {
        String email = email(address), key = key(username, email);
        if (code == null || !code.matches("[0-9]{6}")) throw bad("验证码无效或已过期");
        String id =
                redis.execute(
                        VERIFY,
                        List.of(key, key + ":attempts"),
                        OAuthApplicationService.hash(code));
        if (id == null) throw bad("验证码无效或已过期");
        long uid = Long.parseLong(id);
        var user = users.selectByUserId(uid);
        if (user == null
                || user.getEmail() == null
                || !email.equalsIgnoreCase(user.getEmail())
                || !user.getUsername().equalsIgnoreCase(username.trim())
                || Integer.valueOf(0).equals(user.getStatus())) throw bad("验证码无效或已过期");
        db.queryForObject(
                "SELECT user_id FROM auth_user WHERE user_id=? FOR UPDATE", Long.class, uid);
        invalidate(uid);
        db.update(
                "UPDATE auth_user SET"
                    + " password=?,password_reset_required=1,password_update_time=NOW(),status=IF(status=2,1,status),locked_until=NULL,lock_reason=NULL,fail_count=0"
                    + " WHERE user_id=?",
                encoder.encode("12345678"),
                uid);
        if (attempts != null) attempts.reset(user.getUsername());
        db.update(
                "INSERT INTO auth_password_reset_record(user_id,username,event)"
                        + " VALUES(?,?,'EMAIL_VERIFIED_RESET')",
                uid,
                user.getUsername());
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue()
                .set(
                        "auth:reset:ticket:" + OAuthApplicationService.hash(ticket),
                        id,
                        Duration.ofMinutes(5));
        return ticket;
    }

    public static void validatePassword(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 64
                || "12345678".equals(password)) throw bad("新密码需8~64位，且不能使用默认临时密码");
    }

    @Transactional
    public void complete(String ticket, String password) {
        validatePassword(password);
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw bad("重置凭证无效或已过期");
        String id =
                redis.execute(
                        TAKE, List.of("auth:reset:ticket:" + OAuthApplicationService.hash(ticket)));
        if (id == null) throw bad("重置凭证无效或已过期");
        long uid = Long.parseLong(id);
        var rows =
                db.queryForList(
                        "SELECT username,password_reset_required FROM auth_user WHERE user_id=? FOR"
                                + " UPDATE",
                        uid);
        if (rows.isEmpty()
                || ((Number) rows.getFirst().get("password_reset_required")).intValue() != 1)
            throw bad("重置凭证已失效");
        writePassword(
                uid, rows.getFirst().get("username").toString(), password, "USER_COMPLETED_RESET");
    }

    @Transactional
    public void change(long uid, String oldPassword, String password) {
        validatePassword(password);
        limit("auth:password-change:" + uid, 5, 60);
        var u = users.selectByUserId(uid);
        if (u == null
                || oldPassword == null
                || oldPassword.length() > 64
                || !encoder.matches(oldPassword, u.getPassword())) throw bad("当前密码不正确");
        db.queryForObject(
                "SELECT user_id FROM auth_user WHERE user_id=? FOR UPDATE", Long.class, uid);
        // 条件更新防止并发请求凭旧密码覆盖刚刚修改的新密码。
        invalidate(uid);
        int n =
                db.update(
                        "UPDATE auth_user SET"
                                + " password=?,password_reset_required=0,password_update_time=NOW()"
                                + " WHERE user_id=? AND password=?",
                        encoder.encode(password),
                        uid,
                        u.getPassword());
        if (n != 1) throw bad("密码已变更，请重新验证");
        db.update(
                "INSERT INTO auth_password_reset_record(user_id,username,event)"
                        + " VALUES(?,?,'USER_CHANGED_PASSWORD')",
                uid,
                u.getUsername());
    }

    private void writePassword(long uid, String username, String password, String event) {
        invalidate(uid);
        db.update(
                "UPDATE auth_user SET"
                        + " password=?,password_reset_required=0,password_update_time=NOW() WHERE"
                        + " user_id=?",
                encoder.encode(password),
                uid);
        db.update(
                "INSERT INTO auth_password_reset_record(user_id,username,event) VALUES(?,?,?)",
                uid,
                username,
                event);
    }

    public void requestEmail(long uid, String address, String oldPassword) {
        String email = email(address);
        limit("auth:email-bind:limit:" + uid, 3, 3600);
        var u = users.selectByUserId(uid);
        if (u == null
                || oldPassword == null
                || oldPassword.length() > 64
                || !encoder.matches(oldPassword, u.getPassword())) throw bad("当前密码不正确");
        if (u.getEmail() != null && email.equalsIgnoreCase(u.getEmail())) throw bad("此邮箱已绑定");
        var existing = users.selectByEmail(email);
        if (existing != null && existing.getUserId() != uid) throw bad("邮箱不可用");
        String key = "auth:email-bind:" + uid;
        if (!Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key + ":cooldown", "1", Duration.ofSeconds(60))))
            throw bad("请60秒后再发送验证码");
        String code = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000));
        redis.opsForValue()
                .set(
                        key,
                        OAuthApplicationService.hash(email + "|" + code) + "|" + uid,
                        Duration.ofMinutes(10));
        redis.delete(key + ":attempts");
        try {
            var result = mail.send(new PasswordMailClient.Mail(email, code, "BIND"));
            if (result == null || result.getCode() != 200) throw new IllegalStateException();
        } catch (Exception e) {
            redis.delete(key);
            throw bad("邮件投递失败，请稍后重试");
        }
    }

    @Transactional
    public void bindEmail(long uid, String address, String code) {
        String email = email(address), key = "auth:email-bind:" + uid;
        if (code == null || !code.matches("[0-9]{6}")) throw bad("验证码无效或已过期");
        String result =
                redis.execute(
                        VERIFY,
                        List.of(key, key + ":attempts"),
                        OAuthApplicationService.hash(email + "|" + code));
        if (!Long.toString(uid).equals(result)) throw bad("验证码无效或已过期");
        db.queryForObject(
                "SELECT user_id FROM auth_user WHERE user_id=? FOR UPDATE", Long.class, uid);
        try {
            if (db.update("UPDATE auth_user SET email=? WHERE user_id=?", email, uid) != 1)
                throw bad("账号不存在");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw bad("邮箱不可用");
        }
    }

    public Map<String, Object> security(long uid) {
        var u = users.selectByUserId(uid);
        if (u == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        var result = new LinkedHashMap<String, Object>();
        result.put("username", u.getUsername());
        result.put("email", u.getEmail());
        result.put("passwordUpdatedAt", u.getPasswordUpdateTime());
        return result;
    }

    public List<Map<String, Object>> records() {
        return db.queryForList(
                "SELECT id,user_id AS userId,username,event,create_time AS createTime FROM"
                        + " auth_password_reset_record ORDER BY id DESC LIMIT 1000");
    }

    private static BizException bad(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }
}
