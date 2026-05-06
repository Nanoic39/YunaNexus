package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.entity.OAuthClients;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthLoginTokenVO;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenServiceImpl implements TokenService {

    @Resource
    private YunaRedisService yunaRedisService;

    @Value("${yunanexus.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${yunanexus.auth.jwt.access-exp}")
    private long jwtAccessExp;

    @Value("${yunanexus.auth.jwt.refresh-exp}")
    private long jwtRefreshExp;

    /**
     * 生成密钥
     * @param client OAuth客户端信息
     * @param subject 用户信息
     * @return Token
     */
    @Override
    public OAuthLoginTokenVO generateToken(OAuthClients client, String subject) {
        // accessToken 有效期
        long accessTtl = client.getAccessTokenValidity() == null ? jwtAccessExp : client.getAccessTokenValidity();
        // refreshToken 有效期
        long refreshTtl = client.getRefreshTokenValidity() == null ? jwtRefreshExp : client.getRefreshTokenValidity();

        String jti = UUID.randomUUID().toString().replaceAll("-", "");
        long accessNow = System.currentTimeMillis();
        long accessExp = accessNow + accessTtl * 1000L;

        // 生成 accessToken
        String accessToken = JWT.create()
                .setPayload("sub", subject)
                .setPayload("cid", client.getUuid())
                .setPayload("scope", client.getScope())
                .setPayload("jti", jti)
                .setIssuedAt(new Date(accessNow))
                .setExpiresAt(new Date(accessExp))
                .setKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                .sign();

        // 生成 refreshToken 并使用Redis缓存
        String refreshToken = UUID.randomUUID().toString().replaceAll("-", "");
        // TODO: 从中心获取前缀
        yunaRedisService.set("auth:refresh:" + refreshToken, subject, Duration.ofSeconds(refreshTtl));
        yunaRedisService.set("auth:pair:refresh:" + refreshToken, jti, Duration.ofSeconds(refreshTtl));
        yunaRedisService.set("auth:pair:jti:" + jti, refreshToken, Duration.ofSeconds(refreshTtl));

        // 构造为标准格式后返回
        return OAuthLoginTokenVO.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTtl)
                .scope(client.getScope())
                .build();
    }

    /**
     * 刷新Token
     * @param client OAuth客户端信息
     * @param refreshToken refreshToken
     * @return 刷新后的Token信息
     * TODO: 从中心获取前缀
     */
    @Override
    public OAuthLoginTokenVO refreshToken(OAuthClients client, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }

        String subject = yunaRedisService.get("auth:refresh:" + refreshToken);
        if (!StringUtils.hasText(subject)) {
            return null;
        }

        String oldJti = yunaRedisService.get("auth:pair:refresh:" + refreshToken);
        if (StringUtils.hasText(oldJti)) {
            yunaRedisService.set("auth:blacklist:access:" + oldJti, "1", Duration.ofSeconds(3600));
            yunaRedisService.delete("auth:pair:jti:" + oldJti);
        }

        yunaRedisService.delete("auth:refresh:" + refreshToken);
        yunaRedisService.delete("auth:pair:refresh:" + refreshToken);

        return generateToken(client, subject);
    }

    /**
     * 登出
     * @param accessToken accessToken
     * @return 登出结果(是否成功登出)
     */
    @Override
    public boolean logoutByAccessToken(String accessToken) {
        byte[] key = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // 校验 Token 是否合法
        if (!JWTUtil.verify(accessToken, key)) {
            return false;
        }
        JWT jwt = JWTUtil.parseToken(accessToken);
        Object jtiObj = jwt.getPayload("jti");
        Object expObj = jwt.getPayload("exp"); // 来自setExpiresAt
        if (jtiObj == null || expObj == null) {
            return false; // Token无效
        }

        String jti = String.valueOf(jtiObj);
        long expSeconds = Long.parseLong(String.valueOf(expObj));
        long nowSeconds = System.currentTimeMillis() / 1000;
        if (expSeconds <= nowSeconds) { // 超时时间已经超过当前时间则Token无效
            return false;
        }

        // TODO: 从中心获取前缀
        // 把 jti 放进 Redis 黑名单直到 exp 使 accessToken立即失效
        yunaRedisService.set("auth:blacklist:access:" + jti, "1", Duration.ofSeconds(expSeconds - nowSeconds));
        // 找到并删除 refreshToken 防止续签 Token
        String refreshToken = yunaRedisService.get("auth:pair:jti:" + jti);
        if (StringUtils.hasText(refreshToken)) {
            yunaRedisService.delete("auth:refresh:" + refreshToken);
            yunaRedisService.delete("auth:pair:refresh:" + refreshToken);
            yunaRedisService.delete("auth:pair:jti:" + jti);
        }

        // 登出成功
        return true;
    }

    /**
     * 解析AccessToken，返回用户上下文信息（供其它服务内部调用）
     * @param accessToken accessToken
     * @return 解析结果
     */
    public Map<String, Object> parseAccessToken(String accessToken) {
        if(!StringUtils.hasText(accessToken)) {
            return null;
        }
        byte[] key = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if(!JWTUtil.verify(accessToken, key)) {
            return null;
        }

        JWT jwt = JWTUtil.parseToken(accessToken);
        Object jtiObj = jwt.getPayload("jti");
        Object expObj = jwt.getPayload("exp");
        Object subObj = jwt.getPayload("sub");
        if (jtiObj == null || expObj == null || subObj == null) {
            return null;
        }

        String jti = String.valueOf(jtiObj);
        if (StringUtils.hasText(yunaRedisService.get("auth:blacklist:access:" + jti))) {
            return null;
        }

        long expSeconds;
        if (expObj instanceof Number num) {
            expSeconds = num.longValue();
        } else {
            expSeconds = Long.parseLong(String.valueOf(expObj));
        }
        long nowSeconds = System.currentTimeMillis() / 1000;
        if (expSeconds <= nowSeconds) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("jti", jti);
        result.put("exp", expSeconds);
        result.put("clientUuid", jwt.getPayload("cid"));
        result.put("scope", jwt.getPayload("scope"));

        String sub = String.valueOf(subObj);
        try {
            Map<String, Object> subMap = JSON.parseObject(sub, new TypeReference<Map<String, Object>>() {});
            if(subMap != null) {
                result.putAll(subMap);
            }
        } catch (Exception ignore) {
            result.put("sub", sub);
        }
        return result;
    }
}
