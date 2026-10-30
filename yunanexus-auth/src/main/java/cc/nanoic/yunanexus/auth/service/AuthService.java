package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.EmailCodeLoginRequest;
import cc.nanoic.yunanexus.auth.domain.LoginRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.MeResponse;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;

/**
 * 认证服务（登录 / 刷新 / 登出编排）。<p>
 *
 * 登录支持三种凭证（username / email / mobile），串联状态检查、失败计数锁定、
 * 验证码校验、令牌签发与会话建立。刷新令牌采用轮转（旧 refresh 作废换新），
 * 登出吊销当前会话令牌。
 *
 * @author yunanexus
 */
public interface AuthService {

    /**
     * 登录。
     *
     * @param request 登录请求（含凭证、密码、验证码、客户端标识）
     * @param ip      客户端 IP（落库与审计）
     * @return 令牌响应（access + refresh + 外部 ID）
     */
    LoginResult login(LoginRequest request, String ip);

    /**
     * 邮箱验证码登录（免密码）。
     *
     * @param request 登录请求
     * @param ip      来源 IP
     * @return 登录结果
     */
    LoginResult loginByEmailCode(EmailCodeLoginRequest request, String ip);

    /**
     * 刷新令牌（轮转：旧 refresh 作废，签发新 access + refresh）。
     *
     * @param refreshToken 旧刷新令牌
     * @return 新令牌响应
     */
    LoginResult refresh(String refreshToken);

    /**
     * 登出（吊销当前会话的 access / refresh 令牌并删除会话）。
     *
     * @param accessToken 访问令牌（解析会话后吊销整会话）
     */
    void logout(String accessToken);

    /**
     * 登出所有会话（全局账号撤销：revoke epoch）。
     *
     * @param identity 已认证身份
     */
    void logoutAll(AuthenticatedIdentity identity);

    /**
     * 获取当前身份（仅外部字段）。
     *
     * @param identity 已认证身份
     * @return MeResponse
     */
    MeResponse me(AuthenticatedIdentity identity);

    /**
     * 为源会话签发目标客户端的新令牌对（SSO 子会话）。
     *
     * @param source       源会话
     * @param targetClient 目标客户端标识
     * @return 新令牌响应
     */
    LoginResult issueForSession(SessionInfo source, String targetClient);
}
