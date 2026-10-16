package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

/**
 * 认证服务实现（令牌验签后的会话/纪元/授权复验）。<p>
 *
 * requireSession 不调用 requireGrant，避免递归；授权检查复用私有 helper。
 * 未知用户状态 fail closed；Redis 故障传播；epoch 实时匹配。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class DefaultAuthenticationService implements AuthenticationService {

    private final TokenService tokenService;
    private final SessionService sessionService;
    private final AccountEpochService accountEpochService;
    private final AuthUserMapper authUserMapper;
    private final AuthClientMapper authClientMapper;
    private final AuthPlatformMapper authPlatformMapper;
    private final Clock clock;

    public DefaultAuthenticationService(TokenService tokenService,
                                        SessionService sessionService,
                                        AccountEpochService accountEpochService,
                                        AuthUserMapper authUserMapper,
                                        AuthClientMapper authClientMapper,
                                        AuthPlatformMapper authPlatformMapper,
                                        Clock clock) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.accountEpochService = accountEpochService;
        this.authUserMapper = authUserMapper;
        this.authClientMapper = authClientMapper;
        this.authPlatformMapper = authPlatformMapper;
        this.clock = clock;
    }

    @Override
    public AuthenticatedIdentity authenticateAccess(String accessToken) {
        TokenService.TokenPayload payload = tokenService.verifyAccessToken(accessToken);
        return authenticate(payload, false);
    }

    @Override
    public AuthenticatedIdentity authenticateRefresh(String refreshToken) {
        TokenService.TokenPayload payload = tokenService.verifyRefreshToken(refreshToken);
        return authenticate(payload, true);
    }

    @Override
    public SessionInfo requireSession(String sid) {
        if (sid == null || sid.isBlank()) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        SessionInfo session = sessionService.get(sid);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getSid() == null || !session.getSid().equals(sid)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (session.getAccountEpoch() == null || session.getAccountEpoch().length() != 22) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (session.getUserId() <= 0) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (session.getExpireAtMillis() <= clock.millis()) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        AuthUser user = authUserMapper.selectByUsername(session.getUsername());
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (!Objects.equals(user.getUserId(), session.getUserId())
                || !Objects.equals(user.getExternalId(), session.getExternalId())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        checkUserStatus(user);

        // 纪元实时匹配
        if (!accountEpochService.isCurrent(session.getUserId(), session.getAccountEpoch())) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }

        // 客户端启用 + 平台授权复验
        AuthClient client = authClientMapper.selectByClientCode(session.getClientCode());
        if (client == null) {
            throw new BizException(ErrorCode.CLIENT_NOT_FOUND);
        }
        if (client.getStatus() == null || client.getStatus() != 1) {
            throw new BizException(ErrorCode.CLIENT_DISABLED);
        }
        AuthPlatform platform = authPlatformMapper.selectByUserIdAndClient(session.getUserId(), session.getClientCode());
        if (platform == null || platform.getStatus() == null || platform.getStatus() != 1) {
            throw new BizException(ErrorCode.PLATFORM_AUTH_REVOKED);
        }

        return session;
    }

    @Override
    public AuthClient requireGrant(SessionInfo session, String clientCode) {
        if (session == null || clientCode == null || clientCode.isBlank()) {
            throw new BizException(ErrorCode.CLIENT_NOT_FOUND);
        }
        // 再次检查会话 epoch/expiry/user identity 与 DB 用户状态
        if (session.getAccountEpoch() == null || session.getAccountEpoch().length() != 22) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (session.getExpireAtMillis() <= clock.millis()) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (!accountEpochService.isCurrent(session.getUserId(), session.getAccountEpoch())) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }
        AuthUser user = authUserMapper.selectByUsername(session.getUsername());
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (!Objects.equals(user.getUserId(), session.getUserId())
                || !Objects.equals(user.getExternalId(), session.getExternalId())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        checkUserStatus(user);

        AuthClient target = authClientMapper.selectByClientCode(clientCode);
        if (target == null) {
            throw new BizException(ErrorCode.CLIENT_NOT_FOUND);
        }
        if (target.getStatus() == null || target.getStatus() != 1) {
            throw new BizException(ErrorCode.CLIENT_DISABLED);
        }
        AuthPlatform grant = authPlatformMapper.selectByUserIdAndClient(session.getUserId(), clientCode);
        if (grant == null || grant.getStatus() == null || grant.getStatus() != 1) {
            throw new BizException(ErrorCode.PLATFORM_AUTH_REVOKED);
        }
        return target;
    }

    private AuthenticatedIdentity authenticate(TokenService.TokenPayload payload, boolean refresh) {
        SessionInfo session = requireSession(payload.sid());
        if (!Objects.equals(payload.externalId(), session.getExternalId())
                || !Objects.equals(payload.clientCode(), session.getClientCode())
                || !Objects.equals(payload.sid(), session.getSid())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (refresh) {
            if (!Objects.equals(payload.jti(), session.getRefreshJti())) {
                throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
            }
        } else {
            if (!Objects.equals(payload.jti(), session.getAccessJti())) {
                throw new BizException(ErrorCode.TOKEN_INVALID);
            }
        }
        return new AuthenticatedIdentity(session.getExternalId(), session.getClientCode(), session.getSid(), session.getUserId());
    }

    private void checkUserStatus(AuthUser user) {
        if(Integer.valueOf(1).equals(user.getPasswordResetRequired()))throw new BizException(ErrorCode.TOKEN_REVOKED);
        if (user.getStatus() == null) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (user.getStatus() == 2) {
            throw new BizException(ErrorCode.USER_LOCKED);
        }
        if (user.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
    }
}
