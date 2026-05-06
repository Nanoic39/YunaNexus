package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.OAuthClients;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthLoginTokenVO;

import java.util.Map;

public interface TokenService {
    /**
     * 生成Token
     * @param client OAuth客户端信息
     * @param subject 用户信息
     * @return Token信息
     */
    OAuthLoginTokenVO generateToken(OAuthClients client, String subject);

    /**
     * 刷新Token
     * @param client OAuth客户端信息
     * @param refreshToken refreshToken
     * @return 刷新后的Token信息
     */
    OAuthLoginTokenVO refreshToken(OAuthClients client, String refreshToken);

    /**
     * 登出账户
     * @param accessToken accessToken
     * @return 登出结果(是否成功登出)
     */
    boolean logoutByAccessToken(String accessToken);

    /**
     * 解析AccessToken，返回用户上下文信息（供其它服务内部调用）
     * @param accessToken accessToken
     * @return 解析结果
     */
    Map<String, Object> parseAccessToken(String accessToken);
}
