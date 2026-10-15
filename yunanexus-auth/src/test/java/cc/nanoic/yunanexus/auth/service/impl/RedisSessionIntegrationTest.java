package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "yunanexus.redis.it", matches = "true")
class RedisSessionIntegrationTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static ObjectMapper objectMapper;
    private static AuthRedisKeys keys;

    private String prefix;
    private AuthProperties props;

    @BeforeAll
    static void setupRedis() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        int database = Integer.parseInt(System.getenv().getOrDefault("REDIS_DATABASE", "0"));
        String password = System.getenv("REDIS_PASSWORD");

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(RedisPassword.of(password));
        }
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        objectMapper = new ObjectMapper();

        String pong = redis.execute((RedisConnection connection) -> connection.ping());
        assertEquals("PONG", pong, "Redis ping failed");
    }

    @BeforeEach
    void setupKeys() {
        prefix = "auth-it-" + UUID.randomUUID();
        props = new AuthProperties();
        props.setRedisKeyPrefix(prefix);
        keys = new AuthRedisKeys(props);
    }

    @AfterEach
    void cleanupKeys() {
        assertTrue(prefix.startsWith("auth-it-"), "prefix safety");
        assertTrue(prefix.length() < 128, "prefix length safety");

        ScanOptions scanOptions = ScanOptions.scanOptions().match(prefix + ":*").count(100).build();
        try (Cursor<byte[]> cursor = redis.executeWithStickyConnection(
                connection -> connection.scan(scanOptions))) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
                assertTrue(key.startsWith(prefix + ":"), "key must start with prefix");
                redis.delete(key);
            }
        }
    }

    @AfterAll
    static void teardownRedis() {
        if (factory != null) {
            factory.destroy();
        }
    }

    private static final String FIXED_EPOCH = "AAAAAAAAAAAAAAAAAAAAAA";

    private RedisSessionService createSessionService() {
        return new RedisSessionService(redis, objectMapper, keys);
    }

    private RedisTokenBlacklistService createBlacklistService() {
        return new RedisTokenBlacklistService(redis, keys);
    }

    private SessionInfo newSession(String sid, long userId) {
        long now = Instant.now().toEpochMilli();
        return new SessionInfo(
                sid, userId, "ext-" + sid, "user-" + userId, "web", "device-1",
                "access-jti-" + sid, "refresh-jti-" + sid, now,
                now + Duration.ofMinutes(30).toMillis(),
                now + Duration.ofMinutes(10).toMillis(),
                now + Duration.ofMinutes(30).toMillis(),
                FIXED_EPOCH
        );
    }

    private SessionInfo copyWithNewTokens(SessionInfo original, String newAccessJti, String newRefreshJti, long newExpireAtMillis) {
        long now = Instant.now().toEpochMilli();
        return new SessionInfo(
                original.getSid(), original.getUserId(), original.getExternalId(),
                original.getUsername(), original.getClientCode(), original.getDeviceId(),
                newAccessJti, newRefreshJti, original.getLoginTimeMillis(), newExpireAtMillis,
                now + Duration.ofMinutes(10).toMillis(), newExpireAtMillis,
                original.getAccountEpoch()
        );
    }

    @Test
    void testCreateAndGet() {
        RedisSessionService service = createSessionService();
        String sid = "sid-create-1";
        SessionInfo session = newSession(sid, 1001L);

        service.create(session);

        SessionInfo loaded = service.get(sid);
        assertNotNull(loaded);
        assertEquals(sid, loaded.getSid());
        assertEquals(1001L, loaded.getUserId());
        assertEquals(session.getAccessJti(), loaded.getAccessJti());
        assertEquals(session.getRefreshJti(), loaded.getRefreshJti());
        assertEquals(session.getLoginTimeMillis(), loaded.getLoginTimeMillis());
    }

    @Test
    void testCreateDuplicateThrows() {
        RedisSessionService service = createSessionService();
        String sid = "sid-dup-1";
        service.create(newSession(sid, 1002L));

        assertThrows(IllegalStateException.class, () -> service.create(newSession(sid, 1002L)));
    }

    @Test
    void testRotateConcurrentOnlyOneSuccess() throws Exception {
        RedisSessionService service = createSessionService();
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-rot-1";
        SessionInfo original = newSession(sid, 2001L);
        service.create(original);

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                latch.await();
                SessionInfo updated = copyWithNewTokens(
                        original,
                        "new-access-" + idx,
                        "new-refresh-" + idx,
                        Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
                );
                return service.rotate(sid, original.getRefreshJti(), updated);
            }));
        }

        latch.countDown();
        executor.shutdown();
        for (Future<Boolean> future : futures) {
            if (future.get(20, TimeUnit.SECONDS)) {
                successCount.incrementAndGet();
            }
        }
        assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        assertEquals(1, successCount.get(), "Exactly one rotate should succeed");

        SessionInfo current = service.get(sid);
        assertNotNull(current);
        assertNotEquals(original.getAccessJti(), current.getAccessJti());
        assertNotEquals(original.getRefreshJti(), current.getRefreshJti());

        assertTrue(blacklist.isRevoked(sid, original.getAccessJti()));
        assertTrue(blacklist.isRevoked(sid, original.getRefreshJti()));
    }

    @Test
    void testOldRefreshTokenReplayFails() {
        RedisSessionService service = createSessionService();
        String sid = "sid-replay-1";
        SessionInfo original = newSession(sid, 3001L);
        service.create(original);

        SessionInfo updated = copyWithNewTokens(
                original, "new-access-1", "new-refresh-1",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertTrue(service.rotate(sid, original.getRefreshJti(), updated));

        SessionInfo replayAttempt = copyWithNewTokens(
                original, "new-access-2", "new-refresh-2",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertFalse(service.rotate(sid, original.getRefreshJti(), replayAttempt));
    }

    @Test
    void testRemoveThenGetNull() {
        RedisSessionService service = createSessionService();
        String sid = "sid-remove-1";
        service.create(newSession(sid, 4001L));

        service.remove(sid);
        assertNull(service.get(sid));
    }

    @Test
    void testRotateThenRemoveRevokesNewToken() {
        RedisSessionService service = createSessionService();
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-rotrem-1";
        SessionInfo original = newSession(sid, 5001L);
        service.create(original);

        SessionInfo updated = copyWithNewTokens(
                original, "new-access-1", "new-refresh-1",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertTrue(service.rotate(sid, original.getRefreshJti(), updated));

        service.remove(sid);
        assertNull(service.get(sid));
        assertTrue(blacklist.isRevoked(sid, updated.getAccessJti()));
        assertTrue(blacklist.isRevoked(sid, updated.getRefreshJti()));
    }

    @Test
    void testRaceRotateAndRemove() throws Exception {
        RedisSessionService service = createSessionService();
        RedisTokenBlacklistService blacklist = createBlacklistService();

        int iterations = 10;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < iterations; i++) {
                String sid = "sid-race-" + i;
                SessionInfo original = newSession(sid, 6001L);
                service.create(original);

                CountDownLatch latch = new CountDownLatch(1);
                final int idx = i;
                Future<Boolean> rotateFuture = executor.submit(() -> {
                    latch.await();
                    SessionInfo updated = copyWithNewTokens(
                            original, "race-access-" + idx, "race-refresh-" + idx,
                            Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
                    );
                    return service.rotate(sid, original.getRefreshJti(), updated);
                });
                Future<?> removeFuture = executor.submit(() -> {
                    latch.await();
                    service.remove(sid);
                    return null;
                });

                latch.countDown();
                boolean rotateWon = rotateFuture.get(20, TimeUnit.SECONDS);
                removeFuture.get(20, TimeUnit.SECONDS);

                assertNull(service.get(sid), "Session should not exist after races");
                if (rotateWon) {
                    assertTrue(blacklist.isRevoked(sid, original.getAccessJti()), "Old access token should be revoked if rotate won");
                    assertTrue(blacklist.isRevoked(sid, original.getRefreshJti()), "Old refresh token should be revoked if rotate won");
                    assertTrue(blacklist.isRevoked(sid, "race-access-" + idx), "New access token should be revoked after remove");
                    assertTrue(blacklist.isRevoked(sid, "race-refresh-" + idx), "New refresh token should be revoked after remove");
                }
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void testTtlExpiryAndIndexCleanup() {
        RedisSessionService service = createSessionService();
        String sid = "sid-ttl-1";
        long userId = 7001L;
        long now = Instant.now().toEpochMilli();
        long expireAt = now + Duration.ofMinutes(10).toMillis();
        SessionInfo session = new SessionInfo(
                sid, userId, "ext-ttl", "user-ttl", "web", "device-ttl",
                "access-ttl", "refresh-ttl", now, expireAt,
                now + Duration.ofMinutes(5).toMillis(), expireAt,
                FIXED_EPOCH
        );

        service.create(session);

        String sessionKey = keys.session(sid);
        String indexKey = keys.userSessions(userId);

        Long ttl = redis.getExpire(sessionKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(Math.abs(ttl - Duration.ofMinutes(10).toMillis()) < 5000, "TTL should be approximately 10 minutes");

        Double score = redis.opsForZSet().score(indexKey, sid);
        assertNotNull(score);
        assertTrue(Math.abs(score - expireAt) < 5000, "ZSET score should be actual expiry");

        // Long expiry then short expiry should not shorten TTL
        SessionInfo longerSession = copyWithNewTokens(
                session, "new-access-long", "new-refresh-long",
                now + Duration.ofMinutes(60).toMillis()
        );
        service.rotate(sid, session.getRefreshJti(), longerSession);

        ttl = redis.getExpire(sessionKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > Duration.ofMinutes(30).toMillis(), "TTL should be extended");

        // Expired session should not exist
        String expiredSid = "sid-expired-1";
        long expiredAt = now + Duration.ofMillis(800).toMillis();
        SessionInfo expiredSession = new SessionInfo(
                expiredSid, 7002L, "ext-expired", "user-expired", "web", "device-expired",
                "access-expired", "refresh-expired", now, expiredAt,
                now + Duration.ofMinutes(5).toMillis(), expiredAt,
                FIXED_EPOCH
        );
        service.create(expiredSession);

        // Wait for expiry
        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 3000) {
            if (redis.opsForValue().get(keys.session(expiredSid)) == null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertNull(redis.opsForValue().get(keys.session(expiredSid)), "Expired session key should be removed");
        assertNull(redis.opsForZSet().score(keys.userSessions(7002L), expiredSid), "Expired session should be removed from index");
    }

    @Test
    void testIndexOperationsAndKickAll() {
        RedisSessionService service = createSessionService();
        long userId = 8001L;

        String sid1 = "sid-index-1";
        String sid2 = "sid-index-2";
        service.create(newSession(sid1, userId));
        service.create(newSession(sid2, userId));

        List<String> sids = service.listSidsByUser(userId);
        assertEquals(2, sids.size());
        assertTrue(sids.contains(sid1));
        assertTrue(sids.contains(sid2));

        int kicked = service.kickAll(userId);
        assertEquals(2, kicked);
        assertNull(service.get(sid1));
        assertNull(service.get(sid2));
        assertTrue(service.listSidsByUser(userId).isEmpty());
    }

    @Test
    void testIdentityChangePreventsRotate() {
        RedisSessionService service = createSessionService();
        String sid = "sid-identity-1";
        SessionInfo original = newSession(sid, 9001L);
        service.create(original);

        long now = Instant.now().toEpochMilli();
        SessionInfo differentUser = new SessionInfo(
                sid, 9002L, "different-ext", "different-user", "mobile", "device-2",
                "new-access", "new-refresh", now,
                now + Duration.ofMinutes(30).toMillis(),
                now + Duration.ofMinutes(10).toMillis(),
                now + Duration.ofMinutes(30).toMillis(),
                FIXED_EPOCH
        );
        assertFalse(service.rotate(sid, original.getRefreshJti(), differentUser));
    }

    @Test
    void testExpiredSessionRotateFails() {
        RedisSessionService service = createSessionService();
        String sid = "sid-expired-rot-1";
        long now = Instant.now().toEpochMilli();
        long expireAt = now + Duration.ofMillis(800).toMillis();
        SessionInfo session = new SessionInfo(
                sid, 10001L, "ext-exp-rot", "user-exp-rot", "web", "device-exp-rot",
                "access-exp-rot", "refresh-exp-rot", now, expireAt,
                now + Duration.ofMinutes(5).toMillis(), expireAt,
                FIXED_EPOCH
        );
        service.create(session);

        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 3000) {
            if (redis.opsForValue().get(keys.session(sid)) == null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        SessionInfo updated = copyWithNewTokens(
                session, "new-access", "new-refresh",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertFalse(service.rotate(sid, session.getRefreshJti(), updated));
    }

    @Test
    void testCorruptJsonThrowsOnGet() {
        RedisSessionService service = createSessionService();
        String sid = "sid-corrupt-1";
        service.create(newSession(sid, 11001L));

        String sessionKey = keys.session(sid);
        redis.opsForValue().set(sessionKey, "{invalid json");
        redis.expire(sessionKey, Duration.ofMinutes(10));

        assertThrows(RuntimeException.class, () -> service.get(sid));
    }

    @Test
    void testBlacklistExpiredRevokeRemovesKey() throws Exception {
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-bl-expired-1";
        String jti = "jti-bl-expired-1";
        long now = Instant.now().toEpochMilli();

        blacklist.revoke(sid, jti, Instant.ofEpochMilli(now + Duration.ofMillis(800).toMillis()));
        assertTrue(blacklist.isRevoked(sid, jti));

        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 3000) {
            if (!blacklist.isRevoked(sid, jti)) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertFalse(blacklist.isRevoked(sid, jti), "Expired blacklist entry should be removed");
        assertNull(redis.opsForZSet().score(keys.revoked(sid), jti), "Expired revoked key should not exist");
    }

    @Test
    void testBlacklistExactScoreAndTtlDoNotShortenOther() {
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-bl-exact-1";
        String jtiShort = "jti-short";
        String jtiLong = "jti-long";
        long now = Instant.now().toEpochMilli();
        Instant shortExp = Instant.ofEpochMilli(now + Duration.ofSeconds(2).toMillis());
        Instant longExp = Instant.ofEpochMilli(now + Duration.ofMinutes(60).toMillis());

        blacklist.revoke(sid, jtiLong, longExp);
        blacklist.revoke(sid, jtiShort, shortExp);

        String revokedKey = keys.revoked(sid);
        Double shortScore = redis.opsForZSet().score(revokedKey, jtiShort);
        Double longScore = redis.opsForZSet().score(revokedKey, jtiLong);
        assertNotNull(shortScore);
        assertNotNull(longScore);
        assertEquals(shortExp.toEpochMilli(), shortScore.longValue());
        assertEquals(longExp.toEpochMilli(), longScore.longValue());

        Long ttl = redis.getExpire(revokedKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > Duration.ofMinutes(30).toMillis(), "TTL should reflect longest member, not shortened by short member");

        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 5000) {
            if (redis.opsForZSet().score(revokedKey, jtiShort) == null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long cleanupStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - cleanupStart < 5000) {
            blacklist.isRevoked(sid, jtiShort); // trigger cleanup
            if (redis.opsForZSet().score(revokedKey, jtiShort) == null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertNull(redis.opsForZSet().score(revokedKey, jtiShort), "Expired short member should be removed");
        assertFalse(blacklist.isRevoked(sid, jtiShort), "Short member should be expired");
        assertNotNull(redis.opsForZSet().score(revokedKey, jtiLong), "Long member should remain");
        assertTrue(blacklist.isRevoked(sid, jtiLong));
    }

    @Test
    void testExpiredCandidateRotateReturnsFalseAndKeepsOriginal() {
        RedisSessionService service = createSessionService();
        String sid = "sid-expired-candidate-1";
        long now = Instant.now().toEpochMilli();
        long expireAt = now + Duration.ofMinutes(30).toMillis();
        SessionInfo original = new SessionInfo(
                sid, 14001L, "ext-exp-cand", "user-exp-cand", "web", "device-exp-cand",
                "access-exp-cand", "refresh-exp-cand", now, expireAt,
                now + Duration.ofMinutes(10).toMillis(), expireAt,
                FIXED_EPOCH
        );
        service.create(original);

        SessionInfo expiredCandidate = copyWithNewTokens(
                original, "new-access-exp-cand", "new-refresh-exp-cand",
                now - 1000L
        );
        assertFalse(service.rotate(sid, original.getRefreshJti(), expiredCandidate));

        SessionInfo loaded = service.get(sid);
        assertNotNull(loaded);
        assertEquals(original.getAccessJti(), loaded.getAccessJti());
        assertEquals(original.getRefreshJti(), loaded.getRefreshJti());
    }

    @Test
    void testCorruptAccessExpiryMetadataThrowsBeforeWrite() throws Exception {
        RedisSessionService service = createSessionService();
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-corrupt-exp-1";
        SessionInfo session = newSession(sid, 15001L);
        service.create(session);

        String sessionKey = keys.session(sid);
        com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.valueToTree(session);
        node.put("accessExpiresAtMillis", 0L);
        String corruptJson = objectMapper.writeValueAsString(node);
        redis.opsForValue().set(sessionKey, corruptJson);
        redis.expire(sessionKey, Duration.ofMinutes(10));

        SessionInfo updated = copyWithNewTokens(
                session, "new-access-ce", "new-refresh-ce",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertThrows(RuntimeException.class, () -> service.rotate(sid, session.getRefreshJti(), updated));

        assertEquals(corruptJson, redis.opsForValue().get(sessionKey), "Session JSON should remain unchanged");
        assertThrows(RuntimeException.class, () -> service.remove(sid));
        assertEquals(corruptJson, redis.opsForValue().get(sessionKey), "Session JSON should remain unchanged after remove");
        assertFalse(Boolean.TRUE.equals(redis.hasKey(keys.revoked(sid))), "No revoked key should be created");
        assertFalse(blacklist.isRevoked(sid, updated.getAccessJti()));
        assertFalse(blacklist.isRevoked(sid, updated.getRefreshJti()));

        redis.delete(sessionKey);
        service.create(session);
    }

    @Test
    void testRevokeAlreadyExpiredDoesNotCreateKey() {
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-bl-expired-no-key-1";
        String jti = "jti-bl-expired-no-key-1";

        blacklist.revoke(sid, jti, Instant.now().minusSeconds(1));

        assertFalse(blacklist.isRevoked(sid, jti));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(keys.revoked(sid))));
    }

    @Test
    void testSlotConsistency() {
        String sid = "sid-slot-1";
        String sessionKey = keys.session(sid);
        String revokedKey = keys.revoked(sid);

        assertEquals(SlotHash.getSlot(sessionKey), SlotHash.getSlot(revokedKey),
                "Session and revoked keys must share same slot");
    }

    @Test
    void testLargeUserIdNoScientificNotation() {
        RedisSessionService service = createSessionService();
        long largeUserId = 72057594037927935L;
        String sid = "sid-large-1";
        SessionInfo session = newSession(sid, largeUserId);
        service.create(session);

        SessionInfo loaded = service.get(sid);
        assertNotNull(loaded);
        assertEquals(largeUserId, loaded.getUserId());

        List<String> sids = service.listSidsByUser(largeUserId);
        assertEquals(1, sids.size());
        assertEquals(sid, sids.get(0));

        String indexKey = keys.userSessions(largeUserId);
        assertFalse(indexKey.contains("E"), "No scientific notation in index key");
        assertEquals(prefix + ":user_sessions:{u-72057594037927935}:v2", indexKey);

        service.rotate(sid, session.getRefreshJti(), copyWithNewTokens(
                session, "new-access-large", "new-refresh-large",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        ));

        service.remove(sid);
        assertNull(service.get(sid));
        assertTrue(service.listSidsByUser(largeUserId).isEmpty());
    }

    @Test
    void testRevokedKeyWrongTypeDoesNotAffectSession() {
        RedisSessionService service = createSessionService();
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-wrongtype-1";
        SessionInfo session = newSession(sid, 12001L);
        service.create(session);

        String revokedKey = keys.revoked(sid);
        redis.opsForValue().set(revokedKey, "not-a-zset");

        SessionInfo updated = copyWithNewTokens(
                session, "new-access-wt", "new-refresh-wt",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertThrows(RuntimeException.class, () -> service.rotate(sid, session.getRefreshJti(), updated));

        // Original session JSON remains intact
        SessionInfo loaded = service.get(sid);
        assertNotNull(loaded);
        assertEquals(session.getAccessJti(), loaded.getAccessJti());
        assertEquals(session.getRefreshJti(), loaded.getRefreshJti());
        assertEquals(session.getUserId(), loaded.getUserId());

        // Cleanup: restore revoked key to ZSET so cleanupKeys can delete it
        redis.delete(revokedKey);
    }

    @Test
    void testWrongCurrentMetadataBeforeWrite() {
        RedisSessionService service = createSessionService();
        String sid = "sid-wrongmeta-1";
        SessionInfo session = newSession(sid, 13001L);
        service.create(session);

        String sessionKey = keys.session(sid);

        // Corrupt metadata: set session JSON with invalid fields before rotate (deserialization error path)
        String corruptJson = "{\"sid\":\"" + sid + "\",\"userId\":\"invalid-number\",\"externalId\":\"ext\",\"username\":\"user\",\"clientCode\":\"web\",\"deviceId\":\"device\",\"accessJti\":\"access\",\"refreshJti\":\"refresh\",\"loginTimeMillis\":0,\"expireAtMillis\":0,\"accessExpiresAtMillis\":0,\"refreshExpiresAtMillis\":0}";
        redis.opsForValue().set(sessionKey, corruptJson);
        redis.expire(sessionKey, Duration.ofMinutes(10));

        SessionInfo updated = copyWithNewTokens(
                session, "new-access-wm", "new-refresh-wm",
                Instant.now().toEpochMilli() + Duration.ofMinutes(30).toMillis()
        );
        assertThrows(RuntimeException.class, () -> service.rotate(sid, session.getRefreshJti(), updated));

        // Session JSON remains corrupt, no revoked writes occurred
        assertEquals(corruptJson, redis.opsForValue().get(sessionKey));

        // Restore original session for cleanup
        redis.delete(sessionKey);
        service.create(session);
    }

    @Test
    void testCreateMissingEpochRejected() {
        RedisSessionService service = createSessionService();
        String sid = "sid-missing-epoch-1";
        long now = Instant.now().toEpochMilli();
        SessionInfo session = new SessionInfo(
                sid, 16001L, "ext-missing-epoch", "user-missing-epoch", "web", "device-missing-epoch",
                "access-missing-epoch", "refresh-missing-epoch", now,
                now + Duration.ofMinutes(30).toMillis(),
                now + Duration.ofMinutes(10).toMillis(),
                now + Duration.ofMinutes(30).toMillis()
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(session));
        assertNull(service.get(sid));
    }

    @Test
    void testRotateDifferentEpochRejectedAndCurrentUnchanged() {
        RedisSessionService service = createSessionService();
        RedisTokenBlacklistService blacklist = createBlacklistService();
        String sid = "sid-different-epoch-1";
        SessionInfo original = newSession(sid, 17001L);
        service.create(original);

        long now = Instant.now().toEpochMilli();
        SessionInfo differentEpoch = new SessionInfo(
                sid, original.getUserId(), original.getExternalId(),
                original.getUsername(), original.getClientCode(), original.getDeviceId(),
                "new-access-de", "new-refresh-de", original.getLoginTimeMillis(),
                now + Duration.ofMinutes(30).toMillis(),
                now + Duration.ofMinutes(10).toMillis(),
                now + Duration.ofMinutes(30).toMillis(),
                "BBBBBBBBBBBBBBBBBBBBBB"
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.rotate(sid, original.getRefreshJti(), differentEpoch));

        SessionInfo current = service.get(sid);
        assertNotNull(current);
        assertEquals(original.getAccessJti(), current.getAccessJti());
        assertEquals(original.getRefreshJti(), current.getRefreshJti());
        assertEquals(FIXED_EPOCH, current.getAccountEpoch());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(keys.revoked(sid))));
        assertFalse(blacklist.isRevoked(sid, differentEpoch.getAccessJti()));
        assertFalse(blacklist.isRevoked(sid, differentEpoch.getRefreshJti()));
    }
}
