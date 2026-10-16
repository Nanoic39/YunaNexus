package cc.nanoic.yunanexus.auth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;
import cc.nanoic.yunanexus.common.core.model.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link FeignUserProfileSyncClient} 单元测试。
 *
 * <p>覆盖：outbox → 请求体映射、成功码校验、异常传播、幂等回查语义。
 *
 * @author yunanexus
 */
class FeignUserProfileSyncClientTest {

    private UserProfileSyncFeignClient feignClient;
    private FeignUserProfileSyncClient client;

    @BeforeEach
    void setUp() {
        feignClient = mock(UserProfileSyncFeignClient.class);
        client = new FeignUserProfileSyncClient(feignClient);
    }

    private AuthRegisterOutbox outbox() {
        AuthRegisterOutbox outbox = new AuthRegisterOutbox();
        outbox.setUserId(10001L);
        outbox.setShardIndex(9);
        outbox.setExternalId("ext-10001");
        outbox.setUsername("alice");
        outbox.setEmail("alice@example.com");
        outbox.setMobileCountry("86");
        outbox.setMobile("13800000000");
        return outbox;
    }

    @Test
    void 创建资料映射outbox字段并透传() {
        when(feignClient.create(any())).thenReturn(Result.success(9));

        client.createUserProfile(outbox());

        ArgumentCaptor<UserProfileCreateRequest> captor =
                ArgumentCaptor.forClass(UserProfileCreateRequest.class);
        verify(feignClient).create(captor.capture());
        UserProfileCreateRequest request = captor.getValue();
        assertEquals(10001L, request.getUserId());
        assertEquals(9, request.getShardIndex());
        assertEquals("ext-10001", request.getExternalId());
        assertEquals("alice", request.getUsername());
        assertEquals("alice@example.com", request.getEmail());
        assertEquals("86", request.getMobileCountry());
        assertEquals("13800000000", request.getMobile());
        assertEquals("alice", request.getNickname());
    }

    @Test
    void 创建资料非成功码抛出异常() {
        when(feignClient.create(any())).thenReturn(Result.error(400, "分片不一致"));

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> client.createUserProfile(outbox()));
        assertTrue(ex.getMessage().contains("创建用户资料"));
        assertTrue(ex.getMessage().contains("400"));
    }

    @Test
    void 创建资料无响应抛出异常() {
        when(feignClient.create(any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> client.createUserProfile(outbox()));
    }

    @Test
    void 回查存在返回true() {
        when(feignClient.byUserId(10001L)).thenReturn(Result.success(new UserProfileView()));

        assertTrue(client.exists(10001L));
    }

    @Test
    void 回查不存在返回false() {
        when(feignClient.byUserId(10001L)).thenReturn(Result.success(null));

        assertFalse(client.exists(10001L));
    }

    @Test
    void 回查非成功码抛出异常() {
        when(feignClient.byUserId(10001L)).thenReturn(Result.error(500, "系统内部错误"));

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> client.exists(10001L));
        assertTrue(ex.getMessage().contains("回查用户资料"));
    }
}
