package cc.nanoic.yunanexus.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.auth.client.PasswordMailClient;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordRecoveryServiceTest {
    @Test
    void invalidOtpNeverChangesCredentialsOrSessions() {
        var users = mock(AuthUserMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var db = mock(JdbcTemplate.class);
        var encoder = mock(PasswordEncoder.class);
        var epochs = mock(AccountEpochService.class);
        var sessions = mock(SessionService.class);
        var service =
                new PasswordRecoveryService(
                        users,
                        redis,
                        db,
                        encoder,
                        epochs,
                        sessions,
                        mock(PasswordMailClient.class));
        assertThrows(
                BizException.class, () -> service.verify("alice", "alice@example.com", "000000"));
        verifyNoInteractions(users, db, encoder, epochs, sessions);
    }

    @Test
    void temporaryDefaultPasswordIsRejectedAsNewPermanentPassword() {
        for (String p : new String[] {null, "short", "12345678", "x".repeat(65)})
            assertThrows(BizException.class, () -> PasswordRecoveryService.validatePassword(p));
        assertDoesNotThrow(() -> PasswordRecoveryService.validatePassword("a-safe-new-password"));
    }

    @Test
    void wrongCurrentPasswordCannotChangeAnyAccount() {
        var users = mock(AuthUserMapper.class);
        var u = new AuthUser();
        u.setPassword("stored-bcrypt");
        when(users.selectByUserId(7L)).thenReturn(u);
        var db = mock(JdbcTemplate.class);
        var epochs = mock(AccountEpochService.class);
        var sessions = mock(SessionService.class);
        var encoder = mock(PasswordEncoder.class);
        var redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),eq("60"));
        var service =
                new PasswordRecoveryService(
                        users,
                        redis,
                        db,
                        encoder,
                        epochs,
                        sessions,
                        mock(PasswordMailClient.class));
        assertThrows(BizException.class, () -> service.change(7L, "wrong", "a-safe-new-password"));
        verify(encoder).matches("wrong","stored-bcrypt");
        verifyNoInteractions(db, epochs, sessions);
    }
}
