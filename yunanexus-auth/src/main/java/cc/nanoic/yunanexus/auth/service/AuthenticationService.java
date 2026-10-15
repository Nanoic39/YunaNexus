package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;

/**
 * 认证服务（令牌验签后的会话/纪元/授权复验）。<p>
 *
 * 供 JWT 过滤器与业务服务内部复用。
 *
 * @author yunanexus
 */
public interface AuthenticationService {

    /**
     * 验证 access token，返回服务端身份。
     *
     * @param accessToken access JWT
     * @return 身份（externalId/clientCode/sid/userId）
     */
    AuthenticatedIdentity authenticateAccess(String accessToken);

    /**
     * 验证 refresh token，返回服务端身份。
     *
     * @param refreshToken refresh JWT
     * @return 身份（externalId/clientCode/sid/userId）
     */
    AuthenticatedIdentity authenticateRefresh(String refreshToken);

    /**
     * 加载并校验会话（存在性、过期、epoch、DB 用户状态、客户端/平台授权）。
     *
     * @param sid 会话 ID
     * @return 校验通过的会话
     */
    SessionInfo requireSession(String sid);

    /**
     * 复验目标客户端授权。
     *
     * @param session    已通过 requireSession 的会话
     * @param clientCode 目标客户端标识
     * @return 目标客户端（status=1）
     */
    AuthClient requireGrant(SessionInfo session, String clientCode);
}
