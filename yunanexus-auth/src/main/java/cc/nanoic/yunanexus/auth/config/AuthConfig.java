package cc.nanoic.yunanexus.auth.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * 认证中心基础设施配置。<p>
 *
 * 集中注册认证中心所需的 Bean：<br>
 * 1. {@link PasswordEncoder}：BCrypt（ADR-003）；<br>
 * 2. {@link StringRedisTemplate}：字符串 Redis 模板（会话 / 计数 / 黑名单 / 验证码）；<br>
 * 3. {@link Clock}：系统时钟，便于测试注入可控时钟；<br>
 * 4. {@link RedissonClient}：分布式锁（注册防重，ADR-016），可由 Redis 地址自动装配。<p>
 *
 * @author yunanexus
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class, CaptchaProperties.class, ExternalIdProperties.class, RedissonProperties.class, ShardProperties.class, RegisterOutboxProperties.class, AdminProperties.class})
public class AuthConfig {

    /**
     * BCrypt 密码编码器（默认强度 10）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 字符串 Redis 模板。<p>
     *
     * key / value 均用 String 序列化，会话对象经 Jackson 转 JSON 后以 String 存储，
     * 避免默认 JdkSerializationRedisSerializer 的跨语言不可读与安全风险。
     *
     * @param connectionFactory Redis 连接工厂（spring-boot-starter-data-redis 自动装配）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 系统时钟，登录失败锁定 / 会话过期等时间计算使用；测试可替换为可控时钟。
     */
    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    /**
     * 认证会话专用 ObjectMapper（Jackson 2）。<p>
     *
     * Spring Boot 4 默认 Jackson 3 不提供 {@code com.fasterxml.jackson.databind.ObjectMapper}，
     * 而 RedisSessionService 依赖 Jackson 2（jjwt 传递依赖），因此注册专用 Bean，
     * 供会话 JSON 序列化 / 反序列化使用。不使用全局 default typing，避免反序列化漏洞。
     */
    @Bean(name = "authSessionObjectMapper")
    public com.fasterxml.jackson.databind.ObjectMapper authSessionObjectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * Redisson 分布式锁客户端（ADR-016）。<p>
     *
     * 地址从 {@link RedissonProperties}（绑定 {@code spring.data.redis}）读取，
     * 本地默认 {@code redis://localhost:6379}。生产环境通过环境变量 / Secret 覆盖
     * Redis 地址与密码。
     *
     * @param redisProperties Redis 连接配置（与 spring.data.redis 同源）
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedissonProperties redisProperties) {
        Config config = new Config();
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        org.redisson.config.SingleServerConfig serverConfig = config.useSingleServer().setAddress(address);
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            serverConfig.setPassword(redisProperties.getPassword());
        }
        if (redisProperties.getDatabase() > 0) {
            serverConfig.setDatabase(redisProperties.getDatabase());
        }
        return Redisson.create(config);
    }
}
