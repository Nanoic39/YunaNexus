package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.user.config.UserProperties;
import cc.nanoic.yunanexus.user.domain.CreateUserProfileRequest;
import cc.nanoic.yunanexus.user.domain.SelfUpdateProfileRequest;
import cc.nanoic.yunanexus.user.domain.UpdateUserProfileRequest;
import cc.nanoic.yunanexus.user.domain.UserProfileView;
import cc.nanoic.yunanexus.user.entity.SysUser;
import cc.nanoic.yunanexus.user.mapper.SysUserMapper;
import cc.nanoic.yunanexus.user.shard.ShardRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserProfileServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class UserProfileServiceImplTest {

    private SysUserMapper sysUserMapper;
    private ShardRouter shardRouter;
    private UserProperties properties;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        shardRouter = mock(ShardRouter.class);
        properties = new UserProperties();
        service = new UserProfileServiceImpl(sysUserMapper, shardRouter, properties);
    }

    private CreateUserProfileRequest createRequest(Integer shardIndex) {
        CreateUserProfileRequest request = new CreateUserProfileRequest();
        request.setUserId(1001L);
        request.setShardIndex(shardIndex);
        request.setExternalId("ext-1001");
        request.setUsername("yuna");
        request.setEmail("yuna@example.com");
        request.setMobileCountry("86");
        request.setMobile("13800000000");
        return request;
    }

    private SysUser existing(long userId, int shardIndex) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setShardIndex(shardIndex);
        user.setExternalId("ext-" + userId);
        user.setUsername("existing");
        user.setStatus(SysUser.STATUS_NORMAL);
        return user;
    }

    @Test
    void createInsertsWhenAbsent() {
        when(shardRouter.route(1001L)).thenReturn(7);
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(null);

        int shardIndex = service.createUserProfile(1001L, createRequest(7));

        assertEquals(7, shardIndex);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser inserted = captor.getValue();
        assertEquals(1001L, inserted.getUserId());
        assertEquals(7, inserted.getShardIndex());
        assertEquals("ext-1001", inserted.getExternalId());
        assertEquals("yuna", inserted.getUsername());
        assertEquals(SysUser.STATUS_NORMAL, inserted.getStatus());
    }

    @Test
    void createReturnsExistingWithoutInsertWhenPresent() {
        when(shardRouter.route(1001L)).thenReturn(7);
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(existing(1001L, 7));

        int shardIndex = service.createUserProfile(1001L, createRequest(7));

        assertEquals(7, shardIndex);
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createIsIdempotentOnDuplicateKey() {
        when(shardRouter.route(1001L)).thenReturn(7);
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(null, existing(1001L, 7));
        when(sysUserMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("uk_user_id"));

        int shardIndex = service.createUserProfile(1001L, createRequest(7));

        assertEquals(7, shardIndex);
    }

    @Test
    void createRethrowsWhenDuplicateKeyButRowStillMissing() {
        when(shardRouter.route(1001L)).thenReturn(7);
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(null);
        when(sysUserMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("uk_username"));

        assertThrows(DuplicateKeyException.class, () -> service.createUserProfile(1001L, createRequest(7)));
    }

    @Test
    void createRejectsShardIndexMismatch() {
        when(shardRouter.route(1001L)).thenReturn(7);

        BizException ex = assertThrows(BizException.class,
                () -> service.createUserProfile(1001L, createRequest(2)));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("分片不一致"));
        verify(sysUserMapper, never()).selectByUserId(any(Long.class));
    }

    @Test
    void getByUserIdReturnsView() {
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(existing(1001L, 3));

        UserProfileView view = service.getByUserId(1001L);

        assertEquals(1001L, view.getUserId());
        assertEquals(3, view.getShardIndex());
        assertEquals("ext-1001", view.getExternalId());
    }

    @Test
    void getByUserIdReturnsNullWhenAbsent() {
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(null);
        assertNull(service.getByUserId(1001L));
    }

    @Test
    void existsDelegatesToMapper() {
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(existing(1001L, 1));
        when(sysUserMapper.selectByUserId(2002L)).thenReturn(null);

        assertTrue(service.exists(1001L));
        assertFalse(service.exists(2002L));
    }

    @Test
    void updateProfileThrowsWhenMissing() {
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(null);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setUserId(1001L);
        request.setNickname("n");

        BizException ex = assertThrows(BizException.class, () -> service.updateProfile(1001L, request));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void updateProfileAppliesOnlyNonNullFields() {
        SysUser stored = existing(1001L, 1);
        stored.setNickname("old");
        stored.setAvatar("old-avatar");
        stored.setEmail("old@example.com");
        stored.setMobile("10086");
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(stored);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setUserId(1001L);
        request.setNickname("new");
        request.setEmail("new@example.com");

        service.updateProfile(1001L, request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        SysUser updated = captor.getValue();
        assertEquals("new", updated.getNickname());
        assertEquals("new@example.com", updated.getEmail());
        assertEquals("old-avatar", updated.getAvatar(), "未提供字段应保持原值");
        assertEquals("10086", updated.getMobile(), "未提供字段应保持原值");
    }

    @Test
    void selfUpdateRejectsMobileWhenFeatureDisabled() {
        SelfUpdateProfileRequest request = new SelfUpdateProfileRequest();
        request.setMobile("13800000000");

        BizException ex = assertThrows(BizException.class, () -> service.updateSelfProfile(1001L, request));
        assertEquals(ErrorCode.MOBILE_FEATURE_DISABLED.getCode(), ex.getCode());
        verify(sysUserMapper, never()).selectByUserId(any(Long.class));
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void selfUpdateWithoutMobileSucceedsWhenFeatureDisabled() {
        SysUser stored = existing(1001L, 1);
        stored.setNickname("old");
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(stored);

        SelfUpdateProfileRequest request = new SelfUpdateProfileRequest();
        request.setNickname("new");

        service.updateSelfProfile(1001L, request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertEquals("new", captor.getValue().getNickname());
    }

    @Test
    void selfUpdateAppliesMobileWhenFeatureEnabled() {
        properties.setMobileEnabled(true);
        SysUser stored = existing(1001L, 1);
        when(sysUserMapper.selectByUserId(1001L)).thenReturn(stored);

        SelfUpdateProfileRequest request = new SelfUpdateProfileRequest();
        request.setMobileCountry("+86");
        request.setMobile("13800000000");

        service.updateSelfProfile(1001L, request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertEquals("+86", captor.getValue().getMobileCountry());
        assertEquals("13800000000", captor.getValue().getMobile());
    }
}
