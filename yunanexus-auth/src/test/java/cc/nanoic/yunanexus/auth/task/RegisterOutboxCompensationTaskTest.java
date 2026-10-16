package cc.nanoic.yunanexus.auth.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.nanoic.yunanexus.auth.client.UserProfileSyncClient;
import cc.nanoic.yunanexus.auth.config.RegisterOutboxProperties;
import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;
import cc.nanoic.yunanexus.auth.mapper.AuthRegisterOutboxMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@link RegisterOutboxCompensationTask} 单元测试。
 *
 * <p>覆盖：成功同步、幂等回查短路、失败退避重试、超上限置 FAILED。
 *
 * @author yunanexus
 */
class RegisterOutboxCompensationTaskTest {

    private AuthRegisterOutboxMapper outboxMapper;
    private UserProfileSyncClient syncClient;
    private RegisterOutboxProperties properties;
    private RegisterOutboxCompensationTask task;

    private static final long USER_ID = 10001L;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(AuthRegisterOutboxMapper.class);
        syncClient = mock(UserProfileSyncClient.class);
        properties = new RegisterOutboxProperties();
        properties.setEnabled(true);
        properties.setBatchSize(100);
        properties.setMaxRetry(3);
        properties.setInitialBackoffSeconds(30);
        properties.setMaxBackoffSeconds(3600);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        task = new RegisterOutboxCompensationTask(outboxMapper, syncClient, properties, clock);
    }

    @Test
    void immediateSyncInitializesCommittedRegistration() {
        var user = new cc.nanoic.yunanexus.auth.entity.AuthUser();
        user.setUserId(USER_ID);
        user.setUsername("new_user");
        user.setExternalId("external");
        user.setShardIndex(0);
        when(syncClient.exists(USER_ID)).thenReturn(false);
        task.synchronizeRegistered(user);
        verify(syncClient)
                .createUserProfile(
                        org.mockito.ArgumentMatchers.argThat(
                                row ->
                                        row.getUserId() == USER_ID
                                                && "new_user".equals(row.getUsername())));
        verify(outboxMapper).markDone(eq(USER_ID), any());
    }

    @Test
    void immediateFailureRetainsDurableRetry() {
        var user = new cc.nanoic.yunanexus.auth.entity.AuthUser();
        user.setUserId(USER_ID);
        when(syncClient.exists(USER_ID)).thenThrow(new IllegalStateException("unavailable"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> task.synchronizeRegistered(user));
        verify(outboxMapper).markRetry(eq(USER_ID), eq(1), any(), eq("IllegalStateException"));
        verify(outboxMapper, never()).markDone(anyLong(), any());
    }

    private AuthRegisterOutbox pending(int retryCount) {
        AuthRegisterOutbox r = new AuthRegisterOutbox();
        r.setUserId(USER_ID);
        r.setRetryCount(retryCount);
        r.setStatus(AuthRegisterOutbox.STATUS_PENDING);
        return r;
    }

    @Test
    void 空列表不处理() {
        when(outboxMapper.selectPending(any(), anyInt())).thenReturn(List.of());
        task.compensate();
        verify(syncClient, never()).createUserProfile(any());
        verify(outboxMapper, never()).markDone(anyLong(), any());
    }

    @Test
    void 同步成功置DONE() {
        when(outboxMapper.selectPending(any(), anyInt())).thenReturn(List.of(pending(0)));
        when(syncClient.exists(USER_ID)).thenReturn(false);

        task.compensate();

        verify(syncClient).createUserProfile(any());
        verify(outboxMapper).markDone(eq(USER_ID), any(LocalDateTime.class));
        verify(outboxMapper, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void 幂等回查已存在直接置DONE() {
        when(outboxMapper.selectPending(any(), anyInt())).thenReturn(List.of(pending(0)));
        when(syncClient.exists(USER_ID)).thenReturn(true);

        task.compensate();

        verify(syncClient, never()).createUserProfile(any());
        verify(outboxMapper).markDone(eq(USER_ID), any(LocalDateTime.class));
    }

    @Test
    void 失败按指数退避重试() {
        when(outboxMapper.selectPending(any(), anyInt())).thenReturn(List.of(pending(1)));
        when(syncClient.exists(USER_ID)).thenReturn(false);
        doThrow(new RuntimeException("下游不可用")).when(syncClient).createUserProfile(any());

        task.compensate();

        // retryCount=1 → next=2 → 退避 30 << (2-1) = 60 秒
        verify(outboxMapper)
                .markRetry(eq(USER_ID), eq(2), any(LocalDateTime.class), eq("RuntimeException"));
        verify(outboxMapper, never()).markFailed(anyLong(), anyInt(), any());
    }

    @Test
    void 超过上限置FAILED() {
        // 当前 retryCount=2，maxRetry=3 → 下一次 retry=3 达到上限
        when(outboxMapper.selectPending(any(), anyInt())).thenReturn(List.of(pending(2)));
        when(syncClient.exists(USER_ID)).thenReturn(false);
        doThrow(new RuntimeException("下游不可用")).when(syncClient).createUserProfile(any());

        task.compensate();

        verify(outboxMapper).markFailed(eq(USER_ID), eq(3), eq("RuntimeException"));
        verify(outboxMapper, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void 退避封顶不超过上限() {
        properties.setMaxRetry(10);
        properties.setInitialBackoffSeconds(1000);
        properties.setMaxBackoffSeconds(3600);
        // retryCount=5 → next=6 → 1000 << 5 = 32000 > 3600 → 封顶 3600
        when(outboxMapper.selectPending(any(), anyInt())).thenReturn(List.of(pending(5)));
        when(syncClient.exists(USER_ID)).thenReturn(false);
        doThrow(new RuntimeException("下游不可用")).when(syncClient).createUserProfile(any());

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RegisterOutboxCompensationTask capped =
                new RegisterOutboxCompensationTask(outboxMapper, syncClient, properties, clock);

        capped.compensate();

        LocalDateTime expected = LocalDateTime.now(clock).plusSeconds(3600);
        verify(outboxMapper).markRetry(eq(USER_ID), eq(6), eq(expected), eq("RuntimeException"));
    }
}
