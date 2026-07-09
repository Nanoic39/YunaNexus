package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.DTO.RegisterClientRequest;
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
            vo.setAuditOpinion(c.getAuditOpinion());
            vo.setApplicantGlobalId(c.getApplicantGlobalId() != null
                    ? HexUtil.encodeHexStr(c.getApplicantGlobalId())
                    : null);
            vo.setStatus(c.getStatus());
            vo.setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            vo.setUpdatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
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

    private OAuthClient findClientByUuid(String uuid) {
        OAuthClient client = oAuthClientMapper.selectOne(
                new LambdaQueryWrapper<OAuthClient>()
                        .eq(OAuthClient::getUuid, uuid)
                        .last("LIMIT 1"));
        if (client == null) {
            throw new BusinessException(R.NOT_FOUND, "客户端不存在");
        }
        return client;
    }

    public Map<String, Object> registerClient(RegisterClientRequest req) {
        PermissionUtil.checkLogin();
        byte[] globalId = PermissionContext.getGlobalId();

        String cdKey = "oauth:register:cd:" + HexUtil.encodeHexStr(globalId);
        RBucket<String> cdBucket = redissonClient.getBucket(cdKey);
        if (cdBucket.isExists()) {
            throw new BusinessException(R.PARAM_ERROR, "提交过于频繁，请1小时后再试");
        }

        long pendingCount = oAuthClientMapper.selectCount(
                new LambdaQueryWrapper<OAuthClient>()
                        .eq(OAuthClient::getApplicantGlobalId, globalId)
                        .eq(OAuthClient::getAuditStatus, 0));
        if (pendingCount >= 3) {
            throw new BusinessException(R.PARAM_ERROR, "待审核申请已达上限(3个)，请等待审核结果");
        }

        redissonClient.getBucket(cdKey).set("1", Duration.ofHours(1));

        OAuthClient client = new OAuthClient();
        client.setUuid(cn.hutool.core.util.IdUtil.fastSimpleUUID());
        client.setClientName(req.getClientName());
        client.setClientSecret(BCrypt.hashpw(RandomUtil.randomString(32)));
        client.setClientType(PermissionUtil.hasRole("ADMIN") || PermissionUtil.hasRole("SUPER_ADMIN") ? 1 : 2);
        client.setGrantTypes(req.getGrantTypes() != null ? req.getGrantTypes() : "authorization_code");
        client.setScope(req.getScope() != null ? req.getScope() : "read");
        client.setRedirectUri(req.getRedirectUri());
        client.setDescription(req.getDescription());
        client.setAuditStatus(client.getClientType() == 1 ? 1 : 0);
        client.setApplicantGlobalId(globalId);
        client.setStatus(client.getClientType() == 1 ? 1 : 0);
        client.setCreatedAt(java.time.LocalDateTime.now());
        client.setUpdatedAt(java.time.LocalDateTime.now());
        oAuthClientMapper.insert(client);

        Map<String, Object> result = new HashMap<>();
        result.put("uuid", client.getUuid());
        result.put("clientName", client.getClientName());
        result.put("clientType", client.getClientType());
        result.put("auditStatus", client.getAuditStatus());
        return result;
    }

    public OAuthClientVO getClientDetail(String uuid) {
        PermissionUtil.checkLogin();
        byte[] globalId = PermissionContext.getGlobalId();
        OAuthClient client = findClientByUuid(uuid);

        boolean isOwner = client.getApplicantGlobalId() != null
                && java.util.Arrays.equals(client.getApplicantGlobalId(), globalId);
        boolean isAdmin = PermissionUtil.hasPermission("core:oauth:audit");
        if (!isOwner && !isAdmin) {
            throw new BusinessException(R.NOT_PERMISSION, "无权查看此应用详情");
        }

        OAuthClientVO vo = new OAuthClientVO();
        vo.setId(client.getId());
        vo.setUuid(client.getUuid());
        vo.setClientName(client.getClientName());
        vo.setClientType(client.getClientType());
        vo.setGrantTypes(client.getGrantTypes());
        vo.setScope(client.getScope());
        vo.setRedirectUri(client.getRedirectUri());
        vo.setDescription(client.getDescription());
        vo.setAuditStatus(client.getAuditStatus());
        vo.setAuditOpinion(client.getAuditOpinion());
        vo.setApplicantGlobalId(client.getApplicantGlobalId() != null
                ? HexUtil.encodeHexStr(client.getApplicantGlobalId())
                : null);
        vo.setAuditorGlobalId(client.getAuditorGlobalId() != null
                ? HexUtil.encodeHexStr(client.getAuditorGlobalId())
                : null);
        vo.setAuditedAt(client.getAuditedAt() != null ? client.getAuditedAt().toString() : null);
        vo.setStatus(client.getStatus());
        vo.setCreatedAt(client.getCreatedAt() != null ? client.getCreatedAt().toString() : null);
        vo.setUpdatedAt(client.getUpdatedAt() != null ? client.getUpdatedAt().toString() : null);
        return vo;
    }

    public void updateClient(String uuid, RegisterClientRequest req) {
        PermissionUtil.checkLogin();
        byte[] globalId = PermissionContext.getGlobalId();
        OAuthClient client = findClientByUuid(uuid);

        if (client.getApplicantGlobalId() == null
                || !java.util.Arrays.equals(client.getApplicantGlobalId(), globalId)) {
            throw new BusinessException(R.NOT_PERMISSION, "只能编辑自己的应用");
        }
        if (client.getAuditStatus() == 1 && !PermissionUtil.hasPermission("core:oauth:audit")) {
            throw new BusinessException(R.PARAM_ERROR, "已审核通过的应用不可编辑，请联系管理员");
        }

        if (req.getClientName() != null)
            client.setClientName(req.getClientName());
        if (req.getGrantTypes() != null)
            client.setGrantTypes(req.getGrantTypes());
        if (req.getScope() != null)
            client.setScope(req.getScope());
        if (req.getRedirectUri() != null)
            client.setRedirectUri(req.getRedirectUri());
        if (req.getDescription() != null)
            client.setDescription(req.getDescription());
        client.setUpdatedAt(java.time.LocalDateTime.now());
        oAuthClientMapper.updateById(client);
    }

    public void auditClient(String uuid, Integer auditStatus, String auditOpinion) {
        PermissionUtil.checkPermission("core:oauth:audit");
        byte[] globalId = PermissionContext.getGlobalId();
        OAuthClient client = findClientByUuid(uuid);

        if (client.getAuditStatus() != 0) {
            throw new BusinessException(R.PARAM_ERROR, "该应用已被审核过");
        }
        client.setAuditStatus(auditStatus);
        client.setAuditOpinion(auditOpinion);
        client.setAuditorGlobalId(globalId);
        client.setAuditedAt(java.time.LocalDateTime.now());
        client.setStatus(auditStatus == 1 ? 1 : 0);
        client.setUpdatedAt(java.time.LocalDateTime.now());
        oAuthClientMapper.updateById(client);
    }

    public void toggleClient(String uuid) {
        PermissionUtil.checkPermission("core:oauth:audit");
        OAuthClient client = findClientByUuid(uuid);
        client.setStatus(client.getStatus() == 1 ? 0 : 1);
        client.setUpdatedAt(java.time.LocalDateTime.now());
        oAuthClientMapper.updateById(client);
    }

    public void deleteClient(String uuid) {
        PermissionUtil.checkPermission("core:oauth:audit");
        OAuthClient client = findClientByUuid(uuid);
        oAuthClientMapper.deleteById(client.getId());
    }
}