package cc.nanoic.yunanexus.common.redis.starter;

import cc.nanoic.yunanexus.common.redis.config.RedisProperties;
import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.common.redis.service.impl.YunaRedisServiceImpl;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(RedisProperties.class)
@ConditionalOnProperty(prefix = "yunanexus.redis", name = "enable", havingValue = "true", matchIfMissing = true)
public class YunaNexusRedisAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedisProperties properties) {
        String address = buildAddress(properties);

        Config config = new Config();
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(properties.getDatabase())
                .setConnectTimeout((int) properties.getTimeout().toMillis())
                .setTimeout((int) properties.getTimeout().toMillis());

        return Redisson.create(config);
    }

    private String buildAddress(RedisProperties properties) {
        String protocol = properties.isSsl() ? "rediss://" : "redis://";
        String hostPort = properties.getHost() + ":" + properties.getPort();

        String username = properties.getUsername();
        String password = properties.getPassword();

        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            return protocol + encode(username) + ":" + encode(password) + "@" + hostPort;
        }
        if (StringUtils.hasText(password)) {
            return protocol + ":" + encode(password) + "@" + hostPort;
        }
        return protocol + hostPort;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Bean
    @ConditionalOnMissingBean(YunaRedisService.class)
    public YunaRedisService yunaRedisService(RedissonClient redissonClient) {
        return new YunaRedisServiceImpl(redissonClient);
    }
}