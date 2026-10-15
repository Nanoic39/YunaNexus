package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RedisAccountEpochService 集成测试。
 * 要求：通过 Maven -Dyunanexus.redis.it=true 启动，且提供 REDIS_HOST / REDIS_PORT / REDIS_DATABASE / REDIS_PASSWORD。
 * 使用独立 prefix 隔离，禁止 FLUSHDB / KEYS。
 */
@EnabledIfSystemProperty(named = "yunanexus.redis.it", matches = "true")
class RedisAccountEpochIntegrationTest {

    private static final Pattern EPOCH_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static AuthRedisKeys authRedisKeys;
    private static String testPrefix;

    private RedisAccountEpochService epochService;

    @BeforeAll
    static void setupRedis() {
        String host = envOr("REDIS_HOST", "localhost");
        int port = Integer.parseInt(envOr("REDIS_PORT", "6379"));
        int database = Integer.parseInt(envOr("REDIS_DATABASE", "0"));
        String password = System.getenv("REDIS_PASSWORD");

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(RedisPassword.of(password));
        }

        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        String pong = redisTemplate.execute((RedisCallback<String>) (connection) -> connection.ping());
        assertEquals("PONG", pong, "Redis 必须可达");

        testPrefix = "auth-it-" + UUID.randomUUID().toString();
        AuthProperties props = new AuthProperties();
        props.setRedisKeyPrefix(testPrefix);
        authRedisKeys = new AuthRedisKeys(props);
    }

    @BeforeEach
    void createServiceAndCleanPrefix() {
        epochService = new RedisAccountEpochService(redisTemplate, authRedisKeys);
        cleanTestPrefix();
    }

    @AfterAll
    static void destroy() {
        if (connectionFactory != null) {
            cleanTestPrefix();
            connectionFactory.destroy();
        }
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private static void cleanTestPrefix() {
        if (redisTemplate == null || testPrefix == null) return;
        Set<String> keys = scanKeys(testPrefix + ":*");
        for (String key : keys) {
            if (key.startsWith(testPrefix + ":")) {
                redisTemplate.delete(key);
            }
        }
        Set<String> altKeys = scanKeys(testPrefix + "-alt:*");
        for (String key : altKeys) {
            if (key.startsWith(testPrefix + "-alt:")) {
                redisTemplate.delete(key);
            }
        }
    }

    private static Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) (connection) -> {
            Set<String> found = new HashSet<>();
            try (var cursor = connection.scan(ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build())) {
                while (cursor.hasNext()) {
                    found.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return found;
        });
    }

    @Test
    void currentOrCreate_16并发_返回同一值且合法Base64URL16字节_无TTL永久() throws Exception {
        long userId = 10000000000000L;
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch latch = new CountDownLatch(threads);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    latch.countDown();
                    latch.await();
                    return epochService.currentOrCreate(userId);
                }));
            }

            String first = null;
            for (Future<String> f : futures) {
                String v = f.get(20, TimeUnit.SECONDS);
                assertNotNull(v);
                assertTrue(EPOCH_PATTERN.matcher(v).matches(), "必须 22 位 Base64URL");
                assertEquals(16, Base64.getUrlDecoder().decode(v).length, "解码后必须 16 字节");
                if (first == null) {
                    first = v;
                } else {
                    assertEquals(first, v, "所有并发必须返回同一值");
                }
            }

            Long ttl = redisTemplate.getExpire(authRedisKeys.accountEpoch(userId));
            assertEquals(-1L, ttl, "PTTL 必须为 -1（永久）");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void revoke_oldFalse_新值变化且永久_新登录currentOrCreate返回新值() {
        long userId = 42L;
        String oldEpoch = epochService.currentOrCreate(userId);
        assertTrue(epochService.isCurrent(userId, oldEpoch));

        epochService.revoke(userId);

        assertFalse(epochService.isCurrent(userId, oldEpoch), "旧 epoch 必须失效");
        String newEpoch = epochService.currentOrCreate(userId);
        assertNotEquals(oldEpoch, newEpoch, "revoke 后 currentOrCreate 返回新值");
        assertTrue(EPOCH_PATTERN.matcher(newEpoch).matches());
        assertTrue(epochService.isCurrent(userId, newEpoch));

        Long ttl = redisTemplate.getExpire(authRedisKeys.accountEpoch(userId));
        assertEquals(-1L, ttl, "revoke 后仍是永久");
    }

    @Test
    void missingEpoch_isCurrent_false且不初始化() {
        long userId = 777L;
        assertFalse(epochService.isCurrent(userId, "AAAAAAAAAAAAAAAAAAAAAA"));
        // 确认未初始化
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(authRedisKeys.accountEpoch(userId))),
                "isCurrent 缺失时不得初始化 key");
    }

    @Test
    void wrongType_坏值_currentOrCreate及isCurrent拒绝() {
        long userId = 888L;
        String key = authRedisKeys.accountEpoch(userId);
        redisTemplate.opsForHash().put(key, "field", "value"); // 错误类型

        assertThrows(Exception.class, () -> epochService.currentOrCreate(userId),
                "错误类型 currentOrCreate 必须拒绝");
        assertThrows(Exception.class, () -> epochService.isCurrent(userId, "AAAAAAAAAAAAAAAAAAAAAA"),
                "错误类型 isCurrent 必须拒绝");
    }

    @Test
    void 带TTL损坏epoch拒绝() {
        long userId = 999L;
        String key = authRedisKeys.accountEpoch(userId);
        redisTemplate.opsForValue().set(key, "AAAAAAAAAAAAAAAAAAAAAA");
        redisTemplate.expire(key, Duration.ofSeconds(300)); // 非永久 TTL

        // currentOrCreate 脚本检测到非永久 TTL，拒绝
        assertThrows(RuntimeException.class, () -> epochService.currentOrCreate(userId),
                "带 TTL 的损坏 epoch 必须拒绝");
    }

    @Test
    void uid_2的56次方减1_完整key() {
        long userId = (1L << 56) - 1; // 72057594037927935
        String epoch = epochService.currentOrCreate(userId);
        assertTrue(EPOCH_PATTERN.matcher(epoch).matches());
        assertTrue(epochService.isCurrent(userId, epoch));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(authRedisKeys.accountEpoch(userId))));
    }

    @Test
    void key空间prefix隔离_两个前缀互不影响() {
        // 创建第二个前缀的 service
        String prefix2 = testPrefix + "-alt";
        AuthProperties props2 = new AuthProperties();
        props2.setRedisKeyPrefix(prefix2);
        AuthRedisKeys keys2 = new AuthRedisKeys(props2);
        RedisAccountEpochService service2 = new RedisAccountEpochService(redisTemplate, keys2);

        long userId = 555L;
        String epoch1 = epochService.currentOrCreate(userId);
        String epoch2 = service2.currentOrCreate(userId);

        assertNotEquals(epoch1, epoch2, "不同前缀的纪元必须独立");
        assertTrue(epochService.isCurrent(userId, epoch1));
        assertFalse(epochService.isCurrent(userId, epoch2));
        assertTrue(service2.isCurrent(userId, epoch2));
        assertFalse(service2.isCurrent(userId, epoch1));
    }

    private static void assertNotEquals(Object expected, Object actual, String message) {
        assertFalse(expected.equals(actual), message);
    }
}
