package cc.nanoic.yunanexus.auth.support;

import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class AuthRedisKeyPrefixProvider {

    @Resource
    private YunaRedisService yunaRedisService;

    @Value("${yunanexus.auth.redis.key-prefix:auth}")
    private String defaultKeyPrefix;

    @Value("${yunanexus.auth.redis.key-prefix-config-key:auth:system:config:redis:key-prefix}")
    private String centerConfigKey;

    private final AtomicReference<String> cachedPrefix = new AtomicReference<>("auth");

    @PostConstruct
    public void init() {
        refreshFromCenter();
    }

    @Scheduled(fixedDelayString = "${yunanexus.auth.redis.key-prefix-refresh-ms:30000}", initialDelayString = "5000")
    public void refreshFromCenter() {
        try {
            String remotePrefix = yunaRedisService.get(centerConfigKey);
            if (StringUtils.hasText(remotePrefix)) {
                cachedPrefix.set(remotePrefix.trim());
                return;
            }
        } catch (Exception ignore) {
        }
        cachedPrefix.set(fallbackPrefix());
    }

    private String fallbackPrefix() {
        return StringUtils.hasText(defaultKeyPrefix) ? defaultKeyPrefix.trim() : "auth";
    }

    private String ns() {
        String prefix = cachedPrefix.get();
        return StringUtils.hasText(prefix) ? prefix : fallbackPrefix();
    }

    public String refreshKey(String refreshToken) {
        return ns() + ":refresh:" + refreshToken;
    }

    public String pairRefreshKey(String refreshToken) {
        return ns() + ":pair:refresh:" + refreshToken;
    }

    public String pairJtiKey(String jti) {
        return ns() + ":pair:jti:" + jti;
    }

    public String blacklistAccessKey(String jti) {
        return ns() + ":blacklist:access:" + jti;
    }
}
