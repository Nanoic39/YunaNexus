package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.domain.EmailCodePurpose;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Run against a dedicated local Redis with -Dyunanexus.redis.it=true. No SMTP connection is made. */
@EnabledIfSystemProperty(named = "yunanexus.redis.it", matches = "true")
class EmailCodeServiceRedisIntegrationTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private final Set<String> keys = new LinkedHashSet<>();
    private final List<SimpleMailMessage> messages = new CopyOnWriteArrayList<>();
    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> provider;
    private EmailCodeService service;
    private String email;
    private String ip;

    @BeforeAll
    static void connect() {
        var configuration = new RedisStandaloneConfiguration(
                System.getProperty("yunanexus.redis.host", "127.0.0.1"),
                Integer.getInteger("yunanexus.redis.port", 6379));
        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        try (var connection = factory.getConnection()) {
            assertEquals("PONG", connection.ping());
        }
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) factory.destroy();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        sender = mock(JavaMailSender.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        doAnswer(invocation -> {
            messages.add(new SimpleMailMessage(invocation.getArgument(0, SimpleMailMessage.class)));
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        service = new EmailCodeService(redis, provider, "local-test@example.invalid");
        email = UUID.randomUUID() + "@example.invalid";
        ip = "test-ip-" + UUID.randomUUID();
        remember(email, ip);
    }

    @AfterEach
    void cleanup() {
        redis.delete(keys);
    }

    @Test
    void sendsOnlyToLocalFakeAndStoresExpiringDigestWithoutRecipientInKeys() {
        assertTrue(service.send("  " + email.toUpperCase(Locale.ROOT) + "  ", ip));
        assertEquals(email, messages.getFirst().getTo()[0]);
        assertEquals("local-test@example.invalid", messages.getFirst().getFrom());
        assertTrue(code(messages.getFirst()).matches("[0-9]{6}"));
        String stored = redis.opsForValue().get(key(email));
        assertNotEquals(code(messages.getFirst()), stored.substring(stored.indexOf('|') + 1));
        assertTrue(redis.getExpire(key(email)) > 590);
        assertTrue(redis.getExpire(key(email)) <= 600);
        assertTrue(redis.getExpire(mailboxKey(email) + ":cooldown") > 50);
        assertTrue(keys.stream().noneMatch(k -> k.contains(email) || k.contains(ip)));
        assertTrue(service.verify(email.toUpperCase(Locale.ROOT), code(messages.getFirst())));
        assertFalse(service.verify(email, code(messages.getFirst())));
        assertFalse(redis.hasKey(key(email)));
    }

    @Test
    void invalidEmailAndMissingMailChannelNeverReserveOrSend() {
        for (String invalid : new String[] {null, "", "bad", "a b@example.invalid", "a..b@example.invalid", "a@-bad.example", "a\r\nBcc:b@example.invalid", "a".repeat(255)})
            assertFalse(service.send(invalid, ip));
        when(provider.getIfAvailable()).thenReturn(null);
        assertFalse(service.send(email, ip));
        verifyNoInteractions(sender);
        assertTrue(keys.stream().noneMatch(k -> Boolean.TRUE.equals(redis.hasKey(k))));
    }

    @Test
    void cooldownIsSharedAcrossInstancesAndAllowsOnlyOneConcurrentEmail() throws Exception {
        var second = new EmailCodeService(redis, provider, "local-test@example.invalid");
        List<Boolean> result = concurrent(12, () -> second.send(email, ip));
        assertEquals(1, result.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, messages.size());
        assertFalse(service.send(email, "another-source"));
        assertEquals("1", redis.opsForValue().get(mailboxKey(email) + ":day"));
    }

    @Test
    void verificationIsAtomicAcrossConcurrentConsumers() throws Exception {
        assertTrue(service.send(email, ip));
        String code = code(messages.getFirst());
        List<Boolean> result = concurrent(12, () -> service.verify(email, code));
        assertEquals(1, result.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void fifthWrongCodeDeletesChallengeAndMalformedAttemptsAlsoCount() {
        assertTrue(service.send(email, ip));
        String correct = code(messages.getFirst());
        for (int i = 0; i < 4; i++) assertFalse(service.verify(email, "not-six-digits"));
        assertTrue(redis.hasKey(key(email)));
        assertEquals("4", redis.opsForValue().get(key(email) + ":attempts"));
        assertFalse(service.verify(email, null));
        assertFalse(redis.hasKey(key(email)));
        assertFalse(service.verify(email, correct));
    }

    @Test
    void correctFifthAttemptSucceedsAndClearsAttemptCounter() {
        assertTrue(service.send(email, ip));
        for (int i = 0; i < 4; i++) assertFalse(service.verify(email, "bad"));
        assertTrue(service.verify(email, code(messages.getFirst())));
        assertFalse(redis.hasKey(key(email) + ":attempts"));
    }

    @Test
    void expiredChallengeCannotBeVerifiedAndFailedAttemptTtlDoesNotExtendIt() {
        assertTrue(service.send(email, ip));
        redis.expire(key(email), Duration.ofSeconds(10));
        assertFalse(service.verify(email, "bad"));
        assertTrue(redis.getExpire(key(email) + ":attempts") <= 10);
        redis.expire(key(email), Duration.ZERO);
        assertFalse(service.verify(email, code(messages.getFirst())));
    }

    @Test
    void resendReplacesCodeAndResetsFailureBudget() {
        assertTrue(service.send(email, ip));
        assertFalse(service.verify(email, "bad"));
        String oldValue = redis.opsForValue().get(key(email));
        redis.delete(mailboxKey(email) + ":cooldown");
        assertTrue(service.send(email, ip));
        assertNotEquals(oldValue, redis.opsForValue().get(key(email)));
        assertFalse(redis.hasKey(key(email) + ":attempts"));
        assertTrue(service.verify(email, code(messages.getLast())));
    }

    @Test
    void dailyMailboxQuotaCannotBeBypassedByChangingIp() {
        for (int i = 0; i < 10; i++) {
            redis.delete(mailboxKey(email) + ":cooldown");
            String source = ip + "-" + i;
            remember(email, source);
            assertTrue(service.send(email, source));
        }
        redis.delete(mailboxKey(email) + ":cooldown");
        assertFalse(service.send(email, ip));
        assertEquals(10, messages.size());
        assertTrue(redis.getExpire(mailboxKey(email) + ":day") > 86300);
    }

    @Test
    void sourceQuotaCannotBeBypassedByChangingMailbox() {
        for (int i = 0; i < 20; i++) {
            String address = i + "-" + email;
            remember(address, ip);
            assertTrue(service.send(address, ip));
        }
        assertFalse(service.send(email, ip));
        assertEquals(20, messages.size());
        assertTrue(redis.getExpire(ipKey(ip)) > 590);
        assertFalse(redis.hasKey(mailboxKey(email) + ":cooldown"));
    }

    @Test
    void mailFailureInvalidatesOnlyItsCodeAndAllowsRetryWithinRemainingQuotas() {
        doThrow(new MailSendException("simulated SMTP failure"))
                .when(sender).send(any(SimpleMailMessage.class));
        assertFalse(service.send(email, ip));
        assertFalse(redis.hasKey(key(email)));
        assertFalse(redis.hasKey(mailboxKey(email) + ":cooldown"));
        assertEquals("1", redis.opsForValue().get(mailboxKey(email) + ":day"));
        doNothing().when(sender).send(any(SimpleMailMessage.class));
        assertTrue(service.send(email, ip));
    }

    @Test
    void delayedFailedDeliveryCannotDeleteNewerCodeOrCooldown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0, SimpleMailMessage.class);
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                throw new MailSendException("simulated delayed failure");
            }
            messages.add(new SimpleMailMessage(message));
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> first = executor.submit(() -> service.send(email, ip));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                redis.delete(mailboxKey(email) + ":cooldown"); // Simulate expiry while SMTP is blocked.
                assertTrue(service.send(email, ip));
            } finally {
                release.countDown();
            }
            assertFalse(first.get(10, TimeUnit.SECONDS));
        }
        assertTrue(redis.hasKey(mailboxKey(email) + ":cooldown"));
        assertTrue(service.verify(email, code(messages.getLast())));
    }

    @Test
    void delayedSuccessfulDeliveryReturnsFalseWhenResendHasReplacedItsCode() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } else {
                messages.add(new SimpleMailMessage(
                        invocation.getArgument(0, SimpleMailMessage.class)));
            }
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> first = executor.submit(() -> service.send(email, ip));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                redis.delete(mailboxKey(email) + ":cooldown");
                assertTrue(service.send(email, ip));
            } finally {
                release.countDown();
            }
            assertFalse(first.get(10, TimeUnit.SECONDS));
        }
        assertTrue(redis.hasKey(mailboxKey(email) + ":cooldown"));
        assertTrue(service.verify(email, code(messages.getLast())));
    }

    @Test
    void purposesHaveIndependentChallengesAndFailureBudgetsWithSharedMailboxCooldown() {
        assertTrue(service.send(email, EmailCodePurpose.LOGIN, ip));
        String loginCode = code(messages.getFirst());
        assertTrue(messages.getFirst().getSubject().contains("登录"));
        assertFalse(service.send(email, EmailCodePurpose.REGISTER, ip));
        assertFalse(service.verify(email, loginCode)); // The legacy method means REGISTER only.
        assertFalse(redis.hasKey(key(email)));

        redis.delete(mailboxKey(email) + ":cooldown");
        assertTrue(service.send(email, ip));
        String registerCode = code(messages.getLast());
        assertTrue(messages.getLast().getSubject().contains("注册"));
        for (int i = 0; i < 5; i++) assertFalse(service.verify(email, "wrong"));
        assertFalse(service.verify(email, registerCode));
        assertTrue(service.verify(email, EmailCodePurpose.LOGIN, loginCode));
        assertFalse(service.verify(email, EmailCodePurpose.LOGIN, loginCode));
        assertEquals("2", redis.opsForValue().get(mailboxKey(email) + ":day"));
    }

    @Test
    void unknownPurposeAndUnscopedLegacyChallengesCannotBeUsed() {
        assertFalse(service.send(email, null, ip));
        assertFalse(service.verify(email, null, "123456"));
        verifyNoInteractions(sender);
        redis.opsForValue().set(mailboxKey(email), "legacy|" + hash("123456"), Duration.ofMinutes(10));
        assertFalse(service.verify(email, "123456"));
        assertFalse(service.verify(email, EmailCodePurpose.LOGIN, "123456"));
    }

    @Test
    void dailyQuotaIsSharedAcrossPurposes() {
        for (int i = 0; i < 10; i++) {
            redis.delete(mailboxKey(email) + ":cooldown");
            assertTrue(service.send(email, i % 2 == 0 ? EmailCodePurpose.REGISTER : EmailCodePurpose.LOGIN, ip));
        }
        redis.delete(mailboxKey(email) + ":cooldown");
        assertFalse(service.send(email, EmailCodePurpose.LOGIN, ip));
        assertFalse(service.send(email, EmailCodePurpose.REGISTER, ip));
        assertEquals(10, messages.size());
    }

    private List<Boolean> concurrent(int count, BooleanSupplier action) throws Exception {
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return action.getAsBoolean();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        }
    }

    private void remember(String address, String source) {
        keys.addAll(List.of(key(address), key(address) + ":attempts",
                key(address, EmailCodePurpose.LOGIN), key(address, EmailCodePurpose.LOGIN) + ":attempts",
                mailboxKey(address) + ":cooldown", mailboxKey(address) + ":day", ipKey(source), mailboxKey(address)));
    }

    private static String code(SimpleMailMessage message) {
        var matcher = Pattern.compile("[0-9]{6}").matcher(message.getText());
        assertTrue(matcher.find());
        return matcher.group();
    }

    private static String key(String address) {
        return key(address, EmailCodePurpose.REGISTER);
    }

    private static String key(String address, EmailCodePurpose purpose) {
        return "auth:email-code:" + purpose.name() + ":" + hash(address.trim().toLowerCase(Locale.ROOT));
    }

    private static String mailboxKey(String address) {
        return "auth:email-code:" + hash(address.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String source) {
        return "auth:email-code:ip:" + hash(source);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
