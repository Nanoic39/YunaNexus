package cc.nanoic.yunanexus.auth.oauth2.service;

import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.mapper.OAuthClientMapper;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * OAuth2 客户端查询与认证服务.
 * <p>从 OAuthService 中提取的纯查询逻辑，供授权流程和 Token 端点使用.</p>
 */
@Service
public class OAuth2ClientDetailsService {

    @Resource
    private OAuthClientMapper oAuthClientMapper;

    /**
     * 按 UUID 查找已启用的客户端.
     *
     * @param uuid 客户端 UUID
     * @return 客户端实体，不存在或已禁用返回 null
     */
    public OAuthClient findActiveClient(String uuid) {
        return oAuthClientMapper.selectOne(
                new LambdaQueryWrapper<OAuthClient>()
                        .eq(OAuthClient::getUuid, uuid)
                        .eq(OAuthClient::getStatus, 1)
                        .last("LIMIT 1"));
    }

    /**
     * 认证客户端 — 校验 client_secret.
     *
     * @param clientId     客户端 UUID
     * @param clientSecret 客户端密钥（明文）
     * @return 认证通过返回客户端实体，失败返回 null
     */
    public OAuthClient authenticateClient(String clientId, String clientSecret) {
        OAuthClient client = findActiveClient(clientId);
        if (client == null) {
            return null;
        }
        if (client.getClientSecret() == null) {
            return null;
        }
        if (!BCrypt.checkpw(clientSecret, client.getClientSecret())) {
            return null;
        }
        return client;
    }
}
