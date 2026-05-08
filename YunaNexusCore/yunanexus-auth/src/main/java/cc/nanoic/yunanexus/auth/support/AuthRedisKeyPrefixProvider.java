package cc.nanoic.yunanexus.auth.support;

import cc.nanoic.yunanexus.auth.entity.AuthRedisKeyPrefix;
import cc.nanoic.yunanexus.auth.mapper.AuthRedisKeyPrefixMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthRedisKeyPrefixProvider {

    @Resource
    private AuthRedisKeyPrefixMapper authRedisKeyPrefixMapper;

    @Value("${spring.application.name:YunaNexus-AuthService}")
    private String serviceName;

    /**
     * 建立本地缓存来降低每次发 token/校验 token 时的数据库开销
     * version 用于增量刷新
     */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicLong cacheVersion = new AtomicLong(0);

    /**
     * 启动时拉取数据库内容
     */
    @PostConstruct
    public void init() { refreshFromCenter(); }

    /**
     * 定时刷新
     */
    @Scheduled(fixedDelayString = "${yunanexus.auth.redis.key-template-refresh-ms:30000}", initialDelayString = "5000")
    public void refreshFromCenter() {
        List<AuthRedisKeyPrefix> list = authRedisKeyPrefixMapper.selectList(new LambdaQueryWrapper<AuthRedisKeyPrefix>()
                .eq(AuthRedisKeyPrefix::getServiceName, serviceName)
                .eq(AuthRedisKeyPrefix::getStatus, 1));
        if (list == null || list.isEmpty()) {
            registerDefaultTemplates(); return;
        }
        long v = list.stream().map(AuthRedisKeyPrefix::getVersion).filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L);
        if (v == cacheVersion.get() && !cache.isEmpty()) return;
        cache.clear();
        for (AuthRedisKeyPrefix t : list) if (StringUtils.hasText(t.getKeyCode()) && StringUtils.hasText(t.getKeyTemplate())) cache.put(t.getKeyCode(), t.getKeyTemplate());
        cacheVersion.set(v);
    }

    /**
     * 默认回退，数据库爆炸时这里生效
     */
    private void registerDefaultTemplates() {
        insertIfMissing("AUTH_REFRESH", "auth:refresh:{refreshToken}");
        insertIfMissing("AUTH_PAIR_REFRESH", "auth:pair:refresh:{refreshToken}");
        insertIfMissing("AUTH_PAIR_JTI", "auth:pair:jti:{jti}");
        insertIfMissing("AUTH_BLACKLIST_ACCESS", "auth:blacklist:access:{jti}");
        refreshFromCenter();
    }

    private void insertIfMissing(String code, String template) {
        Long cnt = authRedisKeyPrefixMapper.selectCount(new LambdaQueryWrapper<AuthRedisKeyPrefix>().eq(AuthRedisKeyPrefix::getServiceName, serviceName).eq(AuthRedisKeyPrefix::getKeyCode, code));
        if (cnt != null && cnt > 0) return;
        AuthRedisKeyPrefix t = new AuthRedisKeyPrefix();
        t.setServiceName(serviceName); t.setKeyCode(code); t.setKeyTemplate(template); t.setVersion(1L); t.setStatus(1); t.setRemark("auto-register by auth");
        try { authRedisKeyPrefixMapper.insert(t); } catch (Exception ignore) {}
    }

    private String build(String code, Map<String, String> vars) {
        String tpl = cache.get(code);
        if (!StringUtils.hasText(tpl)) return null;
        for (Map.Entry<String, String> e : vars.entrySet()) tpl = tpl.replace("{" + e.getKey() + "}", e.getValue());
        return tpl;
    }

    public String refreshKey(String refreshToken) {
        return build("AUTH_REFRESH", Map.of("refreshToken", refreshToken));
    }
    public String pairRefreshKey(String refreshToken) {
        return build("AUTH_PAIR_REFRESH", Map.of("refreshToken", refreshToken));
    }
    public String pairJtiKey(String jti) {
        return build("AUTH_PAIR_JTI", Map.of("jti", jti));
    }
    public String blacklistAccessKey(String jti) {
        return build("AUTH_BLACKLIST_ACCESS", Map.of("jti", jti));
    }
}