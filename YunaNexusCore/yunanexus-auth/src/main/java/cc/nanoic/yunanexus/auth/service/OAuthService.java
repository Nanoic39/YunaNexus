package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.DTO.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.LoginResponse;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenResponse;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientVO;
import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.mapper.OAuthClientMapper;
import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.auth.PermissionUtil;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OAuthService {

    @Resource
    private OAuthClientMapper oAuthClientMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private AuthService authService;

    public Map<String, String> authorize(AuthorizeRequest req) {
        OAuthClient client = findActiveClient(req.getClientId());
        if (!req.getRedirectUri().equals(client.getRedirectUri())) {
            throw new BusinessException(R.PARAM_ERROR, "redirect_uri 不匹配");
        }
        if (client.getGrantTypes() == null || !client.getGrantTypes().contains("authorization_code")) {
            throw new BusinessException(R.PARAM_ERROR, "客户端不支持 authorization_code 模式");
        }

        String code = RandomUtil.randomString(32);
        byte[] globalId = PermissionContext.getGlobalId();

        JSONObject codeData = new JSONObject();
        codeData.put("clientId", req.getClientId());
        codeData.put("globalId", HexUtil.encodeHexStr(globalId));
        codeData.put("scope", req.getScope() != null ? req.getScope() : client.getScope());
        codeData.put("redirectUri", req.getRedirectUri());
        if (req.getCodeChallenge() != null) {
            codeData.put("codeChallenge", req.getCodeChallenge());
            codeData.put("codeChallengeMethod",
                    req.getCodeChallengeMethod() != null ? req.getCodeChallengeMethod() : "S256");
        }
        redissonClient.getBucket("oauth:code:" + code).set(codeData.toJSONString(), Duration.ofMinutes(5));

        Map<String, String> result = new HashMap<>();
        result.put("code", code);
        result.put("state", req.getState());
        result.put("redirectUri", req.getRedirectUri());
        return result;
    }

    public TokenResponse token(TokenRequest req) {
        OAuthClient client = findActiveClient(req.getClientId());
        if (client.getClientSecret() == null || !BCrypt.checkpw(req.getClientSecret(), client.getClientSecret())) {
            throw new BusinessException(R.PARAM_ERROR, "客户端密钥错误");
        }

        RBucket<String> bucket = redissonClient.getBucket("oauth:code:" + req.getCode());
        String codeData = bucket.get();
        if (codeData == null) {
            throw new BusinessException(R.PARAM_ERROR, "授权码无效或已过期");
        }

        JSONObject obj = JSON.parseObject(codeData);
        if (!req.getRedirectUri().equals(obj.getString("redirectUri"))) {
            throw new BusinessException(R.PARAM_ERROR, "redirect_uri 不匹配");
        }

        String storedChallenge = obj.getString("codeChallenge");
        if (storedChallenge != null) {
            if (req.getCodeVerifier() == null) {
                throw new BusinessException(R.PARAM_ERROR, "PKCE: 缺少 code_verifier");
            }
            String method = obj.getString("codeChallengeMethod");
            String computed = "plain".equalsIgnoreCase(method)
                    ? req.getCodeVerifier()
                    : Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(DigestUtil.sha256(req.getCodeVerifier()));
            if (!computed.equals(storedChallenge)) {
                throw new BusinessException(R.PARAM_ERROR, "PKCE 验证失败");
            }
        }

        bucket.delete();

        byte[] globalId = HexUtil.decodeHex(obj.getString("globalId"));
        String scope = obj.getString("scope");
        LoginResponse loginResp = authService.issueTokens(globalId);

        TokenResponse resp = new TokenResponse();
        resp.setAccessToken(loginResp.getAccessToken());
        resp.setRefreshToken(loginResp.getRefreshToken());
        resp.setExpiresIn(loginResp.getExpiresIn());
        resp.setTokenType("Bearer");
        resp.setScope(scope);
        resp.setUuid(loginResp.getUuid());
        return resp;
    }

    public List<OAuthClientVO> listClients() {
        byte[] globalId = PermissionContext.getGlobalId();
        boolean canManage = PermissionUtil.hasPermission("core:oauth:list:manage");

        List<OAuthClient> clients;
        if (canManage) {
            clients = oAuthClientMapper.selectList(new LambdaQueryWrapper<OAuthClient>()
                    .orderByDesc(OAuthClient::getCreatedAt));
        } else {
            clients = oAuthClientMapper.selectList(new LambdaQueryWrapper<OAuthClient>()
                    .eq(OAuthClient::getApplicantGlobalId, globalId)
                    .orderByDesc(OAuthClient::getCreatedAt));
        }

        return clients.stream().map(c -> {
            OAuthClientVO vo = new OAuthClientVO();
            vo.setId(c.getId());
            vo.setUuid(c.getUuid());
            vo.setClientName(c.getClientName());
            vo.setClientType(c.getClientType());
            vo.setGrantTypes(c.getGrantTypes());
            vo.setScope(c.getScope());
            vo.setRedirectUri(c.getRedirectUri());
            vo.setDescription(c.getDescription());
            vo.setAuditStatus(c.getAuditStatus());
            vo.setApplicantGlobalId(c.getApplicantGlobalId() != null
                    ? HexUtil.encodeHexStr(c.getApplicantGlobalId()) : null);
            vo.setStatus(c.getStatus());
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    private OAuthClient findActiveClient(String uuid) {
        OAuthClient client = oAuthClientMapper.selectOne(
                new LambdaQueryWrapper<OAuthClient>()
                        .eq(OAuthClient::getUuid, uuid)
                        .eq(OAuthClient::getStatus, 1)
                        .last("LIMIT 1"));
        if (client == null) {
            throw new BusinessException(R.PARAM_ERROR, "客户端不存在或已禁用");
        }
        return client;
    }
}