package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.client.MetaIdClient;
import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.domain.EmailCodePurpose;
import cc.nanoic.yunanexus.auth.domain.RegisterRequest;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.service.RegisterPersistenceService;
import cc.nanoic.yunanexus.auth.service.ShardAllocator;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.core.util.SecureExternalIdGenerator;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegisterServiceImplTest {
    private AuthUserMapper users;
    private MetaIdClient meta;
    private SecureExternalIdGenerator externalIds;
    private PasswordEncoder passwords;
    private RedissonClient redisson;
    private RLock lock;
    private ShardAllocator shards;
    private RegisterPersistenceService persistence;
    private EmailCodeService codes;
    private AuthProperties properties;
    private RegisterServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        users = mock(AuthUserMapper.class);
        meta = mock(MetaIdClient.class);
        externalIds = mock(SecureExternalIdGenerator.class);
        passwords = mock(PasswordEncoder.class);
        redisson = mock(RedissonClient.class);
        lock = mock(RLock.class);
        shards = mock(ShardAllocator.class);
        persistence = mock(RegisterPersistenceService.class);
        codes = mock(EmailCodeService.class);
        properties = new AuthProperties();
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(5, 30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(meta.nextId("user")).thenReturn(Result.success(42L));
        when(shards.allocate(42L)).thenReturn(2);
        when(externalIds.encode(42L, 2)).thenReturn("external-user-id");
        when(passwords.encode("password123")).thenReturn("encoded-password");
        when(codes.verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456")).thenReturn(true);
        service = new RegisterServiceImpl(users, meta, externalIds, passwords, redisson, Clock.systemUTC(),
                shards, persistence, properties, codes);
    }

    @Test
    void normalizesEmailAndConsumesRegisterCodeImmediatelyBeforePersistence() {
        var request = request();
        request.setEmail("  OWNER@EXAMPLE.INVALID  ");
        assertEquals("external-user-id", service.register(request));
        var order = inOrder(users, meta, externalIds, passwords, codes, persistence);
        order.verify(users).selectByUsername("owner");
        order.verify(users).selectByEmail("owner@example.invalid");
        order.verify(meta).nextId("user");
        order.verify(externalIds).encode(42L, 2);
        order.verify(passwords).encode("password123");
        order.verify(codes).verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456");
        var saved = ArgumentCaptor.forClass(AuthUser.class);
        order.verify(persistence).persistWithOutbox(saved.capture());
        assertEquals("owner@example.invalid", saved.getValue().getEmail());
        assertEquals("encoded-password", saved.getValue().getPassword());
        assertEquals(42L, saved.getValue().getUserId());
        verify(lock).unlock();
        verifyNoMoreInteractions(codes);
    }

    @Test
    void missingOrInvalidEmailCannotBypassControllerValidation() {
        for (String invalid : new String[]{null, "", "bad", "a..b@example.invalid", "a@-bad.example",
                "a\r\nBcc:b@example.invalid", "a".repeat(65) + "@example.invalid"}) {
            var request = request();
            request.setEmail(invalid);
            assertCode(ErrorCode.BAD_REQUEST, () -> service.register(request));
        }
        verifyNoInteractions(users, meta, persistence, redisson, codes);
    }

    @Test
    void missingOrMalformedCodeCannotCreateAnUnverifiedAccount() {
        for (String invalid : new String[]{null, "", "12345", "1234567", "abcdef", " 123456 "}) {
            var request = request();
            request.setEmailCode(invalid);
            assertCode(ErrorCode.BAD_REQUEST, () -> service.register(request));
        }
        verifyNoInteractions(users, meta, persistence, redisson, codes);
    }

    @Test
    void rejectedRegisterChallengeNeverPersistsAnAccount() {
        when(codes.verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456")).thenReturn(false);
        assertCode(ErrorCode.BAD_REQUEST, () -> service.register(request()));
        verify(codes).verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456");
        verifyNoInteractions(persistence);
        verify(lock).unlock();
    }

    @Test
    void duplicateUsernameDoesNotConsumeChallengeOrAllocateId() {
        when(users.selectByUsername("owner")).thenReturn(new AuthUser());
        assertCode(ErrorCode.REGISTER_USER_ALREADY_EXISTS, () -> service.register(request()));
        verifyNoInteractions(codes, meta, persistence);
    }

    @Test
    void duplicateNormalizedEmailDoesNotConsumeChallengeOrAllocateId() {
        var request = request();
        request.setEmail("  OWNER@EXAMPLE.INVALID ");
        when(users.selectByEmail("owner@example.invalid")).thenReturn(new AuthUser());
        assertCode(ErrorCode.REGISTER_USER_ALREADY_EXISTS, () -> service.register(request));
        verifyNoInteractions(codes, meta, persistence);
    }

    @Test
    void disabledMobileStillRejectsBothNumberAndCountryWithoutConsumingCode() {
        var request = request();
        request.setMobile("13800000000");
        assertCode(ErrorCode.MOBILE_FEATURE_DISABLED, () -> service.register(request));
        request.setMobile(null);
        request.setMobileCountry("+86");
        assertCode(ErrorCode.MOBILE_FEATURE_DISABLED, () -> service.register(request));
        verifyNoInteractions(codes, meta, persistence);
    }

    @Test
    void idServiceOutagePreservesChallenge() {
        when(meta.nextId("user")).thenReturn(Result.error(500, "offline"));
        assertCode(ErrorCode.REGISTER_FAILED, () -> service.register(request()));
        verifyNoInteractions(codes, persistence);
    }

    @Test
    void encodingFailurePreservesChallenge() {
        when(passwords.encode("password123")).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.register(request()));
        verifyNoInteractions(codes, persistence);
    }

    @Test
    void persistenceFailureDoesNotRestoreConsumedCode() {
        when(codes.verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456"))
                .thenReturn(true, false);
        when(persistence.persistWithOutbox(any())).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.register(request()));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.register(request()));
        verify(persistence, times(1)).persistWithOutbox(any());
        verify(codes, times(2)).verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456");
        verifyNoMoreInteractions(codes);
    }

    @Test
    void databaseUniqueConstraintRaceReturnsConflict() {
        when(persistence.persistWithOutbox(any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertCode(ErrorCode.REGISTER_USER_ALREADY_EXISTS, () -> service.register(request()));
        verify(codes).verify("owner@example.invalid", EmailCodePurpose.REGISTER, "123456");
    }

    private static RegisterRequest request() {
        var request = new RegisterRequest();
        request.setUsername("owner");
        request.setPassword("password123");
        request.setEmail("owner@example.invalid");
        request.setEmailCode("123456");
        return request;
    }

    private static void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected.getCode(), assertThrows(BizException.class, action).getCode());
    }
}
