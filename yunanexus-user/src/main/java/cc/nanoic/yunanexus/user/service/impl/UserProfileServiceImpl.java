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
import cc.nanoic.yunanexus.user.service.UserProfileService;
import cc.nanoic.yunanexus.user.shard.ShardRoute;
import cc.nanoic.yunanexus.user.shard.ShardRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 用户资料服务实现（roadmap 5.5 / 5.6）。
 *
 * @author yunanexus
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileServiceImpl.class);

    private final SysUserMapper sysUserMapper;
    private final ShardRouter shardRouter;
    private final UserProperties properties;

    public UserProfileServiceImpl(SysUserMapper sysUserMapper, ShardRouter shardRouter, UserProperties properties) {
        this.sysUserMapper = sysUserMapper;
        this.shardRouter = shardRouter;
        this.properties = properties;
    }

    @Override
    @ShardRoute
    public int createUserProfile(long userId, CreateUserProfileRequest request) {
        int shardIndex = shardRouter.route(userId);
        if (request.getShardIndex() != null && request.getShardIndex() != shardIndex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分片不一致：认证中心下发 shardIndex=" + request.getShardIndex()
                    + "，用户中心复算=" + shardIndex + "，请检查两侧逻辑分片数配置是否一致");
        }

        SysUser existing = sysUserMapper.selectByUserId(userId);
        if (existing != null) {
            return existing.getShardIndex();
        }

        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setShardIndex(shardIndex);
        user.setExternalId(request.getExternalId());
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setMobileCountry(request.getMobileCountry());
        user.setMobile(request.getMobile());
        user.setStatus(SysUser.STATUS_NORMAL);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发 / outbox 重投下已存在：幂等返回
            SysUser created = sysUserMapper.selectByUserId(userId);
            if (created == null) {
                throw e;
            }
            log.info("用户资料已存在，幂等返回 userId={} shardIndex={}", userId, created.getShardIndex());
            return created.getShardIndex();
        }
        log.info("用户资料创建成功 userId={} shardIndex={}", userId, shardIndex);
        return shardIndex;
    }

    @Override
    @ShardRoute
    public UserProfileView getByUserId(long userId) {
        SysUser user = sysUserMapper.selectByUserId(userId);
        return user == null ? null : UserProfileView.from(user);
    }

    @Override
    @ShardRoute
    public boolean exists(long userId) {
        return sysUserMapper.selectByUserId(userId) != null;
    }

    @Override
    @ShardRoute
    public void updateProfile(long userId, UpdateUserProfileRequest request) {
        SysUser user = sysUserMapper.selectByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        applyChanges(user, request.getNickname(), request.getAvatar(), request.getEmail(),
                request.getMobileCountry(), request.getMobile());
        sysUserMapper.updateById(user);
    }

    @Override
    @ShardRoute
    public void updateSelfProfile(long userId, SelfUpdateProfileRequest request) {
        // 手机号功能默认关闭（无 SMS 能力无法校验归属）：自助改绑手机号一律拒绝
        if (!properties.isMobileEnabled()
                && (hasText(request.getMobile()) || hasText(request.getMobileCountry()))) {
            throw new BizException(ErrorCode.MOBILE_FEATURE_DISABLED);
        }
        SysUser user = sysUserMapper.selectByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        applyChanges(user, request.getNickname(), request.getAvatar(), request.getEmail(),
                request.getMobileCountry(), request.getMobile());
        sysUserMapper.updateById(user);
    }

    /** 仅覆盖非 {@code null} 字段（部分更新）。 */
    private static void applyChanges(SysUser user, String nickname, String avatar, String email,
                                     String mobileCountry, String mobile) {
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (mobileCountry != null) {
            user.setMobileCountry(mobileCountry);
        }
        if (mobile != null) {
            user.setMobile(mobile);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
