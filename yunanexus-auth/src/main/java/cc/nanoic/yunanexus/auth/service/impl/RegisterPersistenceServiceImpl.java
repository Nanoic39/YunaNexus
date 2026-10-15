package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthRegisterOutboxMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.service.RegisterPersistenceService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 注册持久化服务实现。<p>
 *
 * {@code auth_user}、门户 web 授权与 {@code auth_register_outbox} 在同一本地事务内提交：任一失败即整体
 * 回滚，保证「注册成功 ⇔ 有待同步记录」的强一致（ADR-021）。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RegisterPersistenceServiceImpl implements RegisterPersistenceService {

    private final AuthUserMapper authUserMapper;
    private final AuthRegisterOutboxMapper outboxMapper;
    private final Clock clock;
    private final AuthPlatformMapper platformMapper;

    public RegisterPersistenceServiceImpl(AuthUserMapper authUserMapper,
                                          AuthRegisterOutboxMapper outboxMapper,
                                          AuthPlatformMapper platformMapper,
                                          Clock clock) {
        this.authUserMapper = authUserMapper;
        this.outboxMapper = outboxMapper;
        this.platformMapper = platformMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthUser persistWithOutbox(AuthUser user) {
        int inserted = authUserMapper.insert(user);
        if (inserted != 1) {
            throw new BizException(ErrorCode.REGISTER_FAILED, "写入 auth_user 失败");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        // 注册仅授予本站门户；其他平台仍需独立授权，失败则与用户和 outbox 一并回滚。
        AuthPlatform portalGrant = new AuthPlatform();
        portalGrant.setUserId(user.getUserId());
        portalGrant.setClientCode("web");
        portalGrant.setStatus(1);
        portalGrant.setAuthorizedAt(now);
        portalGrant.setCreateTime(now);
        portalGrant.setUpdateTime(now);
        if (platformMapper.insert(portalGrant) != 1) {
            throw new BizException(ErrorCode.REGISTER_FAILED, "写入门户授权失败");
        }

        AuthRegisterOutbox outbox = new AuthRegisterOutbox();
        outbox.setUserId(user.getUserId());
        outbox.setExternalId(user.getExternalId());
        outbox.setShardIndex(user.getShardIndex());
        outbox.setUsername(user.getUsername());
        outbox.setEmail(user.getEmail());
        outbox.setMobileCountry(user.getMobileCountry());
        outbox.setMobile(user.getMobile());
        outbox.setStatus(AuthRegisterOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        int outboxInserted = outboxMapper.insert(outbox);
        if (outboxInserted != 1) {
            throw new BizException(ErrorCode.REGISTER_FAILED, "写入注册 outbox 失败");
        }
        return user;
    }
}
