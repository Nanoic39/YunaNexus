package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话服务实现（Redis）。<p>
 *
 * 存储模型：<br>
 * - {@code auth:{sid}:session:v2} → JSON(SessionInfo)，TTL = 会话过期时间；<br>
 * - {@code auth:{sid}:revoked:v2} → ZSET(jti → 绝对过期毫秒)；<br>
 * - {@code auth:user_sessions:{u-userId}:v2} → ZSET(sid → 会话过期毫秒)。<p>
 *
 * 所有会话操作使用 Lua 脚本原子完成，Redis TIME 权威判定过期。
 * CREATE 必须包含合法 epoch；ROTATE 验证 current/updated epoch 相同且合法。
 * REMOVE 允许清理旧 epoch 缺失会话，但保留过期元数据检查。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RedisSessionService implements SessionService {

    private static final DefaultRedisScript<String> CREATE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<String> REMOVE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> INDEX_UPDATE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<List> INDEX_LIST_SCRIPT = new DefaultRedisScript<>();

    static {
        CREATE_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "local expireAt = tonumber(ARGV[1])\n" +
            "if expireAt <= now then return nil end\n" +
            "local session = cjson.decode(ARGV[2])\n" +
            "if type(session) ~= 'table' then return redis.error_reply('invalid session json') end\n" +
            "if not session.userId or type(session.userId) ~= 'string' then return redis.error_reply('userId must be string') end\n" +
            "if not session.expireAtMillis or type(session.expireAtMillis) ~= 'number' then return redis.error_reply('expireAtMillis must be number') end\n" +
            "if not session.accessJti or type(session.accessJti) ~= 'string' or session.accessJti == '' then return redis.error_reply('accessJti must be non-empty string') end\n" +
            "if not session.refreshJti or type(session.refreshJti) ~= 'string' or session.refreshJti == '' then return redis.error_reply('refreshJti must be non-empty string') end\n" +
            "if session.accessJti == session.refreshJti then return redis.error_reply('accessJti and refreshJti must differ') end\n" +
            "if not session.accountEpoch or type(session.accountEpoch) ~= 'string' or #session.accountEpoch ~= 22 then return redis.error_reply('accountEpoch must be 22-char string') end\n" +
            "local sessionExpireAt = tonumber(session.expireAtMillis)\n" +
            "local accessExpireAt = tonumber(session.accessExpiresAtMillis)\n" +
            "local refreshExpireAt = tonumber(session.refreshExpiresAtMillis)\n" +
            "if not accessExpireAt or accessExpireAt <= now then return redis.error_reply('accessExpiresAtMillis must be > now') end\n" +
            "if not refreshExpireAt or refreshExpireAt <= now then return redis.error_reply('refreshExpiresAtMillis must be > now') end\n" +
            "if sessionExpireAt <= now then return redis.error_reply('expireAtMillis must be > now') end\n" +
            "if sessionExpireAt > refreshExpireAt then return redis.error_reply('expireAtMillis must be <= refreshExpiresAtMillis') end\n" +
            "local setResult = redis.call('SET', KEYS[1], ARGV[2], 'NX')\n" +
            "if not setResult then return false end\n" +
            "redis.call('PEXPIREAT', KEYS[1], sessionExpireAt)\n" +
            "return ARGV[2]"
        );
        CREATE_SCRIPT.setResultType(String.class);

        ROTATE_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "local sessionType = redis.call('TYPE', KEYS[1])['ok']\n" +
            "if sessionType == 'none' then return 0 end\n" +
            "if sessionType ~= 'string' then return redis.error_reply('session key must be string') end\n" +
            "local revokedType = redis.call('TYPE', KEYS[2])['ok']\n" +
            "if revokedType ~= 'none' and revokedType ~= 'zset' then return redis.error_reply('revoked key must be none or zset') end\n" +
            "local sessionJson = redis.call('GET', KEYS[1])\n" +
            "if not sessionJson then return 0 end\n" +
            "local ok, current = pcall(cjson.decode, sessionJson)\n" +
            "if not ok or type(current) ~= 'table' then return redis.error_reply('invalid current session json') end\n" +
            "if not current.userId or type(current.userId) ~= 'string' then return redis.error_reply('current userId must be string') end\n" +
            "if not current.refreshJti or type(current.refreshJti) ~= 'string' or current.refreshJti == '' then return redis.error_reply('current refreshJti must be non-empty string') end\n" +
            "if not current.accessJti or type(current.accessJti) ~= 'string' or current.accessJti == '' then return redis.error_reply('current accessJti must be non-empty string') end\n" +
            "if not current.accountEpoch or type(current.accountEpoch) ~= 'string' or #current.accountEpoch ~= 22 then return redis.error_reply('current accountEpoch must be 22-char string') end\n" +
            "local currentExpireAt = tonumber(current.expireAtMillis)\n" +
            "local currentAccessExpireAt = tonumber(current.accessExpiresAtMillis)\n" +
            "local currentRefreshExpireAt = tonumber(current.refreshExpiresAtMillis)\n" +
            "if not currentExpireAt or not currentAccessExpireAt or not currentRefreshExpireAt then return redis.error_reply('current expiry fields must be numbers') end\n" +
            "if currentExpireAt <= 0 or currentAccessExpireAt <= 0 or currentRefreshExpireAt <= 0 then return redis.error_reply('current expiry fields must be positive') end\n" +
            "if currentExpireAt <= now then return 0 end\n" +
            "if currentRefreshExpireAt <= now then return 0 end\n" +
            "if current.refreshJti ~= ARGV[1] then return 0 end\n" +
            "local updated = cjson.decode(ARGV[2])\n" +
            "if type(updated) ~= 'table' then return redis.error_reply('invalid updated session json') end\n" +
            "if not updated.userId or type(updated.userId) ~= 'string' then return redis.error_reply('updated userId must be string') end\n" +
            "if not updated.accessJti or type(updated.accessJti) ~= 'string' or updated.accessJti == '' then return redis.error_reply('updated accessJti must be non-empty string') end\n" +
            "if not updated.refreshJti or type(updated.refreshJti) ~= 'string' or updated.refreshJti == '' then return redis.error_reply('updated refreshJti must be non-empty string') end\n" +
            "if not updated.accountEpoch or type(updated.accountEpoch) ~= 'string' or #updated.accountEpoch ~= 22 then return redis.error_reply('updated accountEpoch must be 22-char string') end\n" +
            "if updated.accountEpoch ~= current.accountEpoch then return 0 end\n" +
            "if updated.sid ~= current.sid then return 0 end\n" +
            "if updated.userId ~= current.userId then return 0 end\n" +
            "if updated.externalId ~= current.externalId then return 0 end\n" +
            "if updated.username ~= current.username then return 0 end\n" +
            "if updated.clientCode ~= current.clientCode then return 0 end\n" +
            "if updated.deviceId ~= current.deviceId then return 0 end\n" +
            "if tonumber(updated.loginTimeMillis) ~= tonumber(current.loginTimeMillis) then return 0 end\n" +
            "if updated.accessJti == current.accessJti or updated.refreshJti == current.refreshJti " +
            "or updated.accessJti == current.refreshJti or updated.refreshJti == current.accessJti " +
            "or updated.accessJti == updated.refreshJti then return 0 end\n" +
            "local updatedExpireAt = tonumber(updated.expireAtMillis)\n" +
            "local updatedAccessExpireAt = tonumber(updated.accessExpiresAtMillis)\n" +
            "local updatedRefreshExpireAt = tonumber(updated.refreshExpiresAtMillis)\n" +
            "if not updatedExpireAt or not updatedAccessExpireAt or not updatedRefreshExpireAt then return redis.error_reply('updated expiry fields must be numbers') end\n" +
            "if updatedExpireAt <= 0 or updatedAccessExpireAt <= 0 or updatedRefreshExpireAt <= 0 then return redis.error_reply('updated expiry fields must be positive') end\n" +
            "if updatedExpireAt <= now or updatedAccessExpireAt <= now or updatedRefreshExpireAt <= now then return 0 end\n" +
            "if updatedExpireAt > updatedRefreshExpireAt then return 0 end\n" +
            "if currentAccessExpireAt > now then redis.call('ZADD', KEYS[2], currentAccessExpireAt, current.accessJti) end\n" +
            "if currentRefreshExpireAt > now then redis.call('ZADD', KEYS[2], currentRefreshExpireAt, current.refreshJti) end\n" +
            "local maxRevokedScore = redis.call('ZREVRANGE', KEYS[2], 0, 0, 'WITHSCORES')[2]\n" +
            "if maxRevokedScore then redis.call('PEXPIREAT', KEYS[2], maxRevokedScore) end\n" +
            "redis.call('SET', KEYS[1], ARGV[2], 'XX')\n" +
            "redis.call('PEXPIREAT', KEYS[1], updatedExpireAt)\n" +
            "return 1"
        );
        ROTATE_SCRIPT.setResultType(Long.class);

        REMOVE_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "local revokedType = redis.call('TYPE', KEYS[2])['ok']\n" +
            "if revokedType ~= 'none' and revokedType ~= 'zset' then return redis.error_reply('revoked key must be none or zset') end\n" +
            "local sessionType = redis.call('TYPE', KEYS[1])['ok']\n" +
            "if sessionType ~= 'none' and sessionType ~= 'string' then return redis.error_reply('session key must be none or string') end\n" +
            "local sessionJson = redis.call('GET', KEYS[1])\n" +
            "local userId = nil\n" +
            "if sessionJson then\n" +
            "  if sessionType ~= 'string' then return redis.error_reply('session key must be string') end\n" +
            "  local ok, current = pcall(cjson.decode, sessionJson)\n" +
            "  if not ok or type(current) ~= 'table' then return redis.error_reply('invalid session json, refusing removal') end\n" +
            "  if not current.userId or type(current.userId) ~= 'string' then return redis.error_reply('userId must be string') end\n" +
            "  if not current.accessJti or type(current.accessJti) ~= 'string' or current.accessJti == '' then return redis.error_reply('accessJti must be non-empty string') end\n" +
            "  if not current.refreshJti or type(current.refreshJti) ~= 'string' or current.refreshJti == '' then return redis.error_reply('refreshJti must be non-empty string') end\n" +
            "  local currentExpireAt = tonumber(current.expireAtMillis)\n" +
            "  if not currentExpireAt then return redis.error_reply('expireAtMillis must be number') end\n" +
            "  if currentExpireAt <= 0 then return redis.error_reply('expireAtMillis must be positive') end\n" +
            "  local currentAccessExpiry = tonumber(current.accessExpiresAtMillis)\n" +
            "  local currentRefreshExpiry = tonumber(current.refreshExpiresAtMillis)\n" +
            "  if not currentAccessExpiry or not currentRefreshExpiry then return redis.error_reply('current expiry fields must be numbers') end\n" +
            "  if currentAccessExpiry <= 0 or currentRefreshExpiry <= 0 then return redis.error_reply('current expiry fields must be positive') end\n" +
            "  userId = current.userId\n" +
            "  if currentAccessExpiry > now then redis.call('ZADD', KEYS[2], currentAccessExpiry, current.accessJti) end\n" +
            "  if currentRefreshExpiry > now then redis.call('ZADD', KEYS[2], currentRefreshExpiry, current.refreshJti) end\n" +
            "  redis.call('DEL', KEYS[1])\n" +
            "end\n" +
            "local maxRevokedScore = redis.call('ZREVRANGE', KEYS[2], 0, 0, 'WITHSCORES')[2]\n" +
            "if maxRevokedScore then redis.call('PEXPIREAT', KEYS[2], maxRevokedScore) end\n" +
            "if userId then return userId else return nil end"
        );
        REMOVE_SCRIPT.setResultType(String.class);

        INDEX_UPDATE_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "local score = tonumber(ARGV[2])\n" +
            "if score <= now then return 0 end\n" +
            "local existing = redis.call('ZSCORE', KEYS[1], ARGV[1])\n" +
            "if existing and tonumber(existing) > score then\n" +
            "  score = tonumber(existing)\n" +
            "end\n" +
            "redis.call('ZADD', KEYS[1], score, ARGV[1])\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)\n" +
            "local maxScore = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')[2]\n" +
            "if maxScore then redis.call('PEXPIREAT', KEYS[1], maxScore) end\n" +
            "return 1"
        );
        INDEX_UPDATE_SCRIPT.setResultType(Long.class);

        INDEX_LIST_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)\n" +
            "local members = redis.call('ZRANGE', KEYS[1], 0, -1)\n" +
            "local maxScore = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')[2]\n" +
            "if maxScore then redis.call('PEXPIREAT', KEYS[1], maxScore) end\n" +
            "return members"
        );
        INDEX_LIST_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthRedisKeys keys;

    public RedisSessionService(StringRedisTemplate redisTemplate,
                               @org.springframework.beans.factory.annotation.Qualifier("authSessionObjectMapper") ObjectMapper objectMapper,
                               AuthRedisKeys keys) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keys = keys;
    }

    @Override
    public void create(SessionInfo session) {
        validateCreate(session);
        String json = toJson(session);
        try {
            String result = redisTemplate.execute(
                CREATE_SCRIPT,
                Collections.singletonList(keys.session(session.getSid())),
                String.valueOf(session.getExpireAtMillis()),
                json
            );
            if (result == null || "false".equals(result)) {
                throw new IllegalStateException("会话已存在或已过期，不可覆盖活跃 sid");
            }
        } catch (Exception e) {
            log.error("创建会话失败 sid={}", session.getSid(), e);
            throw e;
        }

        try {
            updateUserIndex(session.getUserId(), session.getSid(), session.getExpireAtMillis());
        } catch (Exception e) {
            log.error("创建会话索引失败，撤销会话 sid={}", session.getSid(), e);
            redisTemplate.delete(keys.session(session.getSid()));
            throw e;
        }
    }

    @Override
    public SessionInfo get(String sid) {
        try {
            String json = redisTemplate.opsForValue().get(keys.session(sid));
            if (json == null) {
                return null;
            }
            return fromJson(json);
        } catch (Exception e) {
            log.error("读取会话失败 sid={}", sid, e);
            throw e;
        }
    }

    @Override
    public void remove(String sid) {
        try {
            SessionInfo current = get(sid);
            if (current != null) {
                if (java.util.Objects.equals(current.getAccessJti(), current.getRefreshJti())) {
                    throw new IllegalStateException("当前会话 accessJti 和 refreshJti 相同，数据异常");
                }
                if (current.getExpireAtMillis() > current.getRefreshExpiresAtMillis()) {
                    throw new IllegalStateException("当前会话 expireAt 大于 refreshExpiresAt，数据异常");
                }
            }
            String userIdStr = redisTemplate.execute(
                REMOVE_SCRIPT,
                List.of(keys.session(sid), keys.revoked(sid))
            );
            if (userIdStr != null) {
                long userId = Long.parseLong(userIdStr);
                removeUserIndexMember(userId, sid);
            }
        } catch (Exception e) {
            log.error("删除会话失败 sid={}", sid, e);
            throw e;
        }
    }

    @Override
    public List<String> listSidsByUser(long userId) {
        try {
            List<String> sids = redisTemplate.execute(
                INDEX_LIST_SCRIPT,
                Collections.singletonList(keys.userSessions(userId))
            );
            if (sids == null) {
                throw new IllegalStateException("用户索引列表脚本返回 null userId=" + userId);
            }
            if (sids.isEmpty()) {
                return List.of();
            }
            List<String> valid = new ArrayList<>();
            for (String sid : sids) {
                String json = redisTemplate.opsForValue().get(keys.session(sid));
                if (json == null) {
                    removeUserIndexMember(userId, sid);
                    continue;
                }
                SessionInfo info = fromJson(json);
                if (info.getUserId() == userId) {
                    valid.add(sid);
                } else {
                    removeUserIndexMember(userId, sid);
                }
            }
            return valid;
        } catch (Exception e) {
            log.error("列出用户会话失败 userId={}", userId, e);
            throw e;
        }
    }

    @Override
    public int kickAll(long userId) {
        List<String> sids = listSidsByUser(userId);
        int count = 0;
        for (String sid : sids) {
            SessionInfo session = get(sid);
            if (session == null) {
                continue;
            }
            remove(sid);
            count++;
        }
        log.info("踢出用户全部会话 userId={} 数量={}", userId, count);
        return count;
    }

    @Override
    public boolean rotate(String sid, String expectedRefreshJti, SessionInfo updated) {
        if (sid == null || expectedRefreshJti == null || updated == null) {
            throw new IllegalArgumentException("参数不能为 null");
        }
        validateRotate(updated);
        if (java.util.Objects.equals(updated.getAccessJti(), updated.getRefreshJti())) {
            throw new IllegalArgumentException("accessJti 和 refreshJti 不能相同");
        }
        SessionInfo current = get(sid);
        if (current == null) {
            return false;
        }
        if (java.util.Objects.equals(current.getAccessJti(), current.getRefreshJti())) {
            throw new IllegalStateException("当前会话 accessJti 和 refreshJti 相同，数据异常");
        }
        if (current.getExpireAtMillis() > current.getRefreshExpiresAtMillis()) {
            throw new IllegalStateException("当前会话 expireAt 大于 refreshExpiresAt，数据异常");
        }
        if (!java.util.Objects.equals(current.getAccountEpoch(), updated.getAccountEpoch())) {
            throw new IllegalArgumentException("轮转不能变更账户撤销标识");
        }
        if (!java.util.Objects.equals(sid, updated.getSid())
            || current.getUserId() != updated.getUserId()
            || !java.util.Objects.equals(current.getExternalId(), updated.getExternalId())
            || !java.util.Objects.equals(current.getUsername(), updated.getUsername())
            || !java.util.Objects.equals(current.getClientCode(), updated.getClientCode())
            || !java.util.Objects.equals(current.getDeviceId(), updated.getDeviceId())
            || current.getLoginTimeMillis() != updated.getLoginTimeMillis()
            || !java.util.Objects.equals(expectedRefreshJti, current.getRefreshJti())) {
            return false;
        }
        String json = toJson(updated);
        try {
            Long indexResult = redisTemplate.execute(
                INDEX_UPDATE_SCRIPT,
                Collections.singletonList(keys.userSessions(updated.getUserId())),
                sid,
                String.valueOf(updated.getExpireAtMillis())
            );
            if (indexResult == null) {
                throw new IllegalStateException("用户索引预写失败 sid=" + sid);
            }
            if (indexResult == 0L) {
                return false;
            }
            if (indexResult != 1L) {
                throw new IllegalStateException("用户索引预写异常结果 sid=" + sid + " result=" + indexResult);
            }
            Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(keys.session(sid), keys.revoked(sid)),
                expectedRefreshJti,
                json
            );
            if (result == null) {
                throw new IllegalStateException("轮转脚本返回 null sid=" + sid);
            }
            if (result != 0L && result != 1L) {
                return false;
            }
            return result == 1L;
        } catch (Exception e) {
            log.error("轮转会话失败 sid={}", sid, e);
            throw e;
        }
    }

    private void validateCreate(SessionInfo session) {
        if (session == null) {
            throw new IllegalArgumentException("会话信息不能为 null");
        }
        if (isBlank(session.getSid()) || isBlank(session.getExternalId())
            || isBlank(session.getUsername()) || isBlank(session.getClientCode())
            || isBlank(session.getAccessJti()) || isBlank(session.getRefreshJti())) {
            throw new IllegalArgumentException("会话身份字段不能为空");
        }
        if (session.getUserId() <= 0) {
            throw new IllegalArgumentException("userId 必须 > 0");
        }
        if (session.getAccountEpoch() == null || session.getAccountEpoch().length() != 22) {
            throw new IllegalArgumentException("accountEpoch 必须为 22 字符");
        }
        long now = System.currentTimeMillis();
        if (session.getExpireAtMillis() <= now
            || session.getAccessExpiresAtMillis() <= now
            || session.getRefreshExpiresAtMillis() <= now) {
            throw new IllegalArgumentException("会话过期时间必须 > now");
        }
        if (session.getExpireAtMillis() > session.getRefreshExpiresAtMillis()) {
            throw new IllegalArgumentException("expireAt 必须 <= refreshExpiresAt");
        }
    }

    private void validateRotate(SessionInfo updated) {
        if (isBlank(updated.getSid()) || isBlank(updated.getExternalId())
            || isBlank(updated.getUsername()) || isBlank(updated.getClientCode())
            || isBlank(updated.getAccessJti()) || isBlank(updated.getRefreshJti())) {
            throw new IllegalArgumentException("更新会话身份字段不能为空");
        }
        if (updated.getUserId() <= 0) {
            throw new IllegalArgumentException("userId 必须 > 0");
        }
        if (updated.getAccountEpoch() == null || updated.getAccountEpoch().length() != 22) {
            throw new IllegalArgumentException("accountEpoch 必须为 22 字符");
        }
        if (updated.getExpireAtMillis() <= 0
            || updated.getAccessExpiresAtMillis() <= 0
            || updated.getRefreshExpiresAtMillis() <= 0) {
            throw new IllegalArgumentException("更新会话过期时间必须为合法正数");
        }
        if (updated.getExpireAtMillis() > updated.getRefreshExpiresAtMillis()) {
            throw new IllegalArgumentException("expireAt 必须 <= refreshExpiresAt");
        }
    }

    private void updateUserIndex(long userId, String sid, long expireAtMillis) {
        Long result = redisTemplate.execute(
            INDEX_UPDATE_SCRIPT,
            Collections.singletonList(keys.userSessions(userId)),
            sid,
            String.valueOf(expireAtMillis)
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("用户索引更新失败 userId=" + userId + " sid=" + sid);
        }
    }

    private void removeUserIndexMember(long userId, String sid) {
        Long result = redisTemplate.opsForZSet().remove(keys.userSessions(userId), sid);
        if (result == null) {
            throw new IllegalStateException("用户索引删除失败 userId=" + userId + " sid=" + sid);
        }
    }

    private String toJson(SessionInfo session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception e) {
            throw new IllegalStateException("会话序列化失败", e);
        }
    }

    private SessionInfo fromJson(String json) {
        try {
            return objectMapper.readValue(json, SessionInfo.class);
        } catch (Exception e) {
            log.error("会话反序列化失败");
            throw new IllegalStateException("会话反序列化失败", e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
