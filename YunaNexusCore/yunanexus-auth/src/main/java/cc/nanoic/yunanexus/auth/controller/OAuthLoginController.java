package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.OAuthLoginRequest;
import cc.nanoic.yunanexus.auth.entity.OAuthClients;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthLoginTokenVO;
import cc.nanoic.yunanexus.auth.service.OAuthClientsService;
import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@RestController
@RequestMapping("/oauth")
public class OAuthLoginController {

    @Resource
    private OAuthClientsService oAuthClientsService;

    @Resource
    private YunaRedisService yunaRedisService;

    @PostMapping("/login")
    public Result<OAuthLoginTokenVO> login(@RequestBody OAuthLoginRequest req) {
        // 参数校验
        if (!StringUtils.hasText(req.getGrantType())
                || !StringUtils.hasText(req.getClientName())
                || !StringUtils.hasText(req.getClientSecret())) {
            return Result.fail(R.PARAM_ERROR, "grantType/clientName/clientSecret不能为空");
        }

        OAuthClients client = oAuthClientsService.findByClientName(req.getClientName());
        if (client == null) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端不存在或已被禁用.");
        }

        if (!containsGrantType(client.getAuthorizedGrantTypes(), req.getGrantType())) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端未开通该授权模式.");
        }

        if (!verifySecret(req.getClientSecret(), client.getClientSecret())) {
            return Result.fail(R.ACCOUNT_ERROR, "客户端密钥错误.");
        }

        // 如果 Password 模式
        if ("password".equals(req.getGrantType())) {
            // 校验用户名密码
            if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
                return Result.fail(R.PARAM_ERROR, "用户名或密码不能为空!");
            }
        }

        // 如果为 refresh_token模式
        if ("refresh_token".equals(req.getGrantType()) && !StringUtils.hasText(req.getRefreshToken())) {
            return Result.fail(R.PARAM_ERROR, "refresh_token不能为空!");
        }

        // 过期时间
        long accessTtl = client.getAccessTokenValidity() == null ? 7200L : client.getAccessTokenValidity();
        long refreshTokenTtl = client.getRefreshTokenValidity() == null ? 604800L : client.getRefreshTokenValidity();

        // TODO: 这里需要对接真实登录-OpenFeign+Nacos服务名
        OAuthLoginTokenVO token = OAuthLoginTokenVO.builder()
        .tokenType("Bearer")
        .accessToken(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .expiresIn(accessTtl)
        // .scope()
        .build();

        return Result.success(token);
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
            return false;
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .anyMatch(target::equals);
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
}
