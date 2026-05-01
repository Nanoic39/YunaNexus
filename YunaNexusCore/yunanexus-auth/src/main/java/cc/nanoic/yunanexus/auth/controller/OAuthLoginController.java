package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.client.UserInternalClient;
import cc.nanoic.yunanexus.auth.entity.DTO.OAuthLoginRequest;
import cc.nanoic.yunanexus.auth.entity.OAuthClients;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthLoginTokenVO;
import cc.nanoic.yunanexus.auth.service.OAuthClientsService;
import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/oauth")
public class OAuthLoginController {

    @Resource
    private OAuthClientsService oAuthClientsService;

    @Resource
    private YunaRedisService yunaRedisService;

    @Resource
    private UserInternalClient userInternalClient;

    //TODO: 要不要拆分不同接口的RequestBody?

    /**
     * 登录接口
     * @param req OAuthLoginRequest
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<OAuthLoginTokenVO> login(@RequestBody OAuthLoginRequest req) {
        // 参数校验
        if (!StringUtils.hasText(req.getGrantType())
                || !StringUtils.hasText(req.getClientUuid())
                || !StringUtils.hasText(req.getClientSecret())) {
            return Result.fail(R.PARAM_ERROR, "grantType/clientUuid/clientSecret不能为空");
        }

        OAuthClients client = oAuthClientsService.findByUuid(req.getClientUuid());
        if (client == null) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端不存在或已被禁用.");
        }

        if (containsGrantType(client.getAuthorizedGrantTypes(), req.getGrantType())) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端未开通该授权模式.");
        }

        if (!verifySecret(req.getClientSecret(), client.getClientSecret())) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端密钥错误.");
        }

        // 如果 Password 模式
        String subject;
        switch (req.getGrantType()) {
            case "password" -> {
                // 校验用户名密码
                if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
                    return Result.fail(R.PARAM_ERROR, "用户名或密码不能为空!");
                }

                subject = verifyByUserService(req.getUsername(), req.getPassword());
                if (!StringUtils.hasText(subject)) {
                    return Result.fail(R.ACCOUNT_ERROR, "账号或密码错误");
                }
            }
            case "client_credentials" -> subject = "client:" + client.getUuid();
            case "refresh_token" -> {
                if (!StringUtils.hasText(req.getRefreshToken())) {
                    return Result.fail(R.PARAM_ERROR, "refreshToken不能为空!");
                }
                subject = yunaRedisService.get("auth:refresh:" + req.getRefreshToken());
                if (!StringUtils.hasText(subject)) {
                    return Result.fail(R.TOKEN_ALL_EXPIRED, "refreshToken已失效");
                }
            }
            case null, default -> {
                return Result.fail(R.PARAM_ERROR, "不支持的grantType");
            }
        }

        OAuthLoginTokenVO token = generateOAuthToken(client, subject);

        return Result.success(token, "登录成功");
    }

    /**
     * 刷新Token
     * @param req OAuthLoginRequest
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    public Result<OAuthLoginTokenVO> refresh(@RequestBody OAuthLoginRequest req) {
        if (!StringUtils.hasText(req.getClientUuid())
                || !StringUtils.hasText(req.getClientSecret())
                || !StringUtils.hasText(req.getRefreshToken())) {
            return Result.fail(R.PARAM_ERROR, "clientUuid/clientSecret/refreshToken不能为空");
        }

        OAuthClients client = oAuthClientsService.findByUuid(req.getClientUuid());
        if (client == null) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端不存在或已被禁用.");
        }
        if (containsGrantType(client.getAuthorizedGrantTypes(), "refresh_token")) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端未开通refresh_token授权模式.");
        }
        if (!verifySecret(req.getClientSecret(), client.getClientSecret())) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端密钥错误.");
        }

        String oldRefreshToken = req.getRefreshToken();
        String subject = yunaRedisService.get("auth:refresh:" + oldRefreshToken);
        if (!StringUtils.hasText(subject)) {
            return Result.fail(R.TOKEN_ALL_EXPIRED, "refreshToken已失效");
        }

        // 先生成新的token
        OAuthLoginTokenVO token = generateOAuthToken(client, subject);
        // 再删除旧的refreshToken
        yunaRedisService.delete("auth:refresh:" + oldRefreshToken);

        return Result.success(token, "刷新成功");
    }



    /**
     * 校验客户端是否授权该grantType
     * 
     * @param configured 客户端配置的grantType
     * @param target     请求的grantType
     * @return 是否授权该grantType
     */
    private boolean containsGrantType(String configured, String target) {
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(target)) {
            return true;
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .noneMatch(target::equals);
    }

    /**
     * 校验客户端密钥是否正确
     * 
     * @param raw          原始密钥
     * @param encodedOrRaw 编码后的密钥或原始密钥
     * @return 是否正确
     */
    private boolean verifySecret(String raw, String encodedOrRaw) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(encodedOrRaw)) {
            return false;
        }
        if (encodedOrRaw.startsWith("$2a$") || encodedOrRaw.startsWith("$2b$") || encodedOrRaw.startsWith("$2y$")) {
            return BCrypt.checkpw(raw, encodedOrRaw);
        }
        return raw.equals(encodedOrRaw);
    }

    /**
     * 通过 UserService 服务校验登录信息
     * @param username 账号
     * @param password 密码
     * @return Uuid结果
     */
    private String verifyByUserService(String username, String password) {
        try {
            Map<String, String> req = new HashMap<>();
            req.put("username", username);
            req.put("password", password);
            Result<Map<String, Object>> resp = userInternalClient.verifyUser(req);
            if (resp == null || resp.getCode() != R.SUCCESS.getCode() || resp.getData() == null) {
                return null;
            }
            // 这里字段需要和 UserController 中返回的字段保持一致
            Object userUuid = resp.getData().get("userUuid");
            return userUuid == null ? null : String.valueOf(userUuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 公共方法，生成Token并写入Redis
     * @param client 查询到的OAuth客户端信息
     * @param subject 登录用户UUID(作为Redis键值)
     * @return 构造好的Token内容
     */
    private OAuthLoginTokenVO generateOAuthToken(OAuthClients client, String subject) {
        // 过期时间
        long accessTtl = client.getAccessTokenValidity() == null ? 7200L : client.getAccessTokenValidity();
        long refreshTokenTtl = client.getRefreshTokenValidity() == null ? 604800L : client.getRefreshTokenValidity();

        String accessToken = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        yunaRedisService.set("auth:access:" + accessToken, subject, Duration.ofSeconds(accessTtl));
        yunaRedisService.set("auth:refresh:" + refreshToken, subject, Duration.ofSeconds(refreshTokenTtl));

        return OAuthLoginTokenVO.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTtl)
                .scope(client.getScope())
                .build();
    }
}
