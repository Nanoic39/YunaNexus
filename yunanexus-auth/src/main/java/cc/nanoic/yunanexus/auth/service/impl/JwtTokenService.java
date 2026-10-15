package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.config.JwtProperties;
import cc.nanoic.yunanexus.auth.service.TokenBlacklistService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 令牌服务实现（kid 版本化，ADR-015）。<p>
 *
 * 使用 jjwt 0.12.x：RS256 签名，头携带 {@code kid}。签发使用 {@code activeKid}
 * 指定的私钥，验证按 {@code kid} 通过 {@code keyLocator} 选择公钥，实现密钥平滑轮换。
 * 仅接受 RS256 算法，拒绝 HMAC / none / RS512 等算法。
 *
 * 令牌负载约定：<br>
 * - {@code sub}：外部 ID；<br>
 * - {@code typ}：{@code access} / {@code refresh}；<br>
 * - {@code client}：客户端标识；<br>
 * - {@code sid}：会话 ID；<br>
 * - {@code jti}：唯一 ID，供黑名单吊销；<br>
 * - {@code iss}、{@code iat}、{@code exp}：标准声明。<p>
 *
 * 不再包含 {@code uid}/{@code userId}/{@code ext} 字段，旧版含 {@code uid} 的令牌直接拒绝。
 * 敏感信息（签名密钥、令牌本身）一律不打印（standards.md §十）。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class JwtTokenService implements TokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_CLIENT = "client";
    private static final String CLAIM_SID = "sid";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final int MAX_TOKEN_LENGTH = 16384;
    private static final int MIN_RSA_BITS = 2048;
    private static final java.util.regex.Pattern KID_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final AuthProperties properties;
    private final TokenBlacklistService blacklistService;
    private final Clock clock;

    /** kid -> 公钥（启动时从配置构建，不可变） */
    private final Map<String, PublicKey> publicKeyByKid;

    /** active kid -> 私钥（仅一份，签发使用） */
    private final String activeKid;
    private final PrivateKey activePrivateKey;

    public JwtTokenService(AuthProperties properties,
                           JwtProperties jwtProperties,
                           TokenBlacklistService blacklistService,
                           Clock clock) {
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.clock = clock;

        validateTtlAndIssuer(properties);

        Map<String, PublicKey> publicKeys = new LinkedHashMap<>();
        String configuredActiveKid = jwtProperties.getActiveKid();
        PrivateKey privateKey = null;

        List<JwtProperties.SigningKey> keys = jwtProperties.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("未配置任何 JWT 签名密钥（yunanexus.auth.jwt.keys）");
        }
        for (JwtProperties.SigningKey sk : keys) {
            if (sk == null) {
                throw new IllegalStateException("JWT 签名密钥配置存在空项");
            }
            if (sk.getKid() == null || sk.getKid().isBlank() || !KID_PATTERN.matcher(sk.getKid()).matches()) {
                throw new IllegalStateException("JWT 签名密钥缺少或非法 kid");
            }
            if (sk.getPublicKeyBase64() == null || sk.getPublicKeyBase64().isBlank()) {
                if (sk.getKid().equals(configuredActiveKid)) {
                    throw new IllegalStateException("JWT active kid " + sk.getKid() + " 缺少 public-key-base64");
                }
                // 非 active 版本未注入公钥视为尚未启用，跳过（支持仅预置配置、按需注入密钥的多版本共存）
                continue;
            }
            PublicKey publicKey = decodePublicKey(sk.getPublicKeyBase64(), sk.getKid());
            validateRsaKey(publicKey, sk.getKid());
            if (publicKeys.put(sk.getKid(), publicKey) != null) {
                throw new IllegalStateException("JWT 签名密钥 kid 重复: " + sk.getKid());
            }

            if (!sk.getKid().equals(configuredActiveKid) && sk.getPrivateKeyBase64() != null && !sk.getPrivateKeyBase64().isBlank()) {
                throw new IllegalStateException("JWT 非 active kid " + sk.getKid() + " 不应配置私钥");
            }
            if (sk.getKid().equals(configuredActiveKid)) {
                if (sk.getPrivateKeyBase64() == null || sk.getPrivateKeyBase64().isBlank()) {
                    throw new IllegalStateException("JWT active kid " + sk.getKid() + " 缺少 private-key-base64");
                }
                PrivateKey candidate = decodePrivateKey(sk.getPrivateKeyBase64(), sk.getKid());
                validateRsaKey(candidate, sk.getKid());
                if (!isKeyPairMatching(publicKey, candidate)) {
                    throw new IllegalStateException("JWT active kid " + sk.getKid() + " 公私钥不匹配");
                }
                privateKey = candidate;
            }
        }

        if (configuredActiveKid == null || configuredActiveKid.isBlank() || !KID_PATTERN.matcher(configuredActiveKid).matches()) {
            throw new IllegalStateException("未配置或非法 JWT active-kid");
        }
        if (!publicKeys.containsKey(configuredActiveKid)) {
            throw new IllegalStateException("JWT active-kid 未知: " + configuredActiveKid);
        }
        if (privateKey == null) {
            throw new IllegalStateException("JWT active-kid 缺少私钥: " + configuredActiveKid);
        }

        this.publicKeyByKid = Map.copyOf(publicKeys);
        this.activeKid = configuredActiveKid;
        this.activePrivateKey = privateKey;
    }

    @Override
    public String issueAccessToken(String externalId, String clientCode, String sid, String jti) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return buildToken(TYPE_ACCESS, externalId, clientCode, sid, jti,
                now, now.plusSeconds(properties.getAccessTokenTtlSeconds()));
    }

    @Override
    public String issueRefreshToken(String externalId, String clientCode, String sid, String jti) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return buildToken(TYPE_REFRESH, externalId, clientCode, sid, jti,
                now, now.plusSeconds(properties.getRefreshTokenTtlSeconds()));
    }

    @Override
    public TokenPair issueTokenPair(String externalId, String clientCode, String sid, String accessJti, String refreshJti) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant accessExpiresAt = now.plusSeconds(properties.getAccessTokenTtlSeconds());
        Instant refreshExpiresAt = now.plusSeconds(properties.getRefreshTokenTtlSeconds());
        String accessToken = buildToken(TYPE_ACCESS, externalId, clientCode, sid, accessJti, now, accessExpiresAt);
        String refreshToken = buildToken(TYPE_REFRESH, externalId, clientCode, sid, refreshJti, now, refreshExpiresAt);
        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    @Override
    public TokenPayload verifyAccessToken(String token) {
        return verify(token, TYPE_ACCESS);
    }

    @Override
    public TokenPayload verifyRefreshToken(String token) {
        return verify(token, TYPE_REFRESH);
    }

    // ---- 内部方法 ----

    private void validateTtlAndIssuer(AuthProperties properties) {
        if (properties.getIssuer() == null || properties.getIssuer().isBlank() || properties.getIssuer().length() > 256) {
            throw new IllegalStateException("JWT issuer 不能为空且长度不能超过256");
        }
        if (properties.getAccessTokenTtlSeconds() <= 0) {
            throw new IllegalStateException("JWT access token TTL 必须大于0");
        }
        if (properties.getRefreshTokenTtlSeconds() <= 0) {
            throw new IllegalStateException("JWT refresh token TTL 必须大于0");
        }
    }

    /**
     * 构建 JWT。使用 activeKid 对应私钥进行 RS256 签名。
     */
    private String buildToken(String type, String externalId, String clientCode,
                              String sid, String jti, Instant issuedAt, Instant expiresAt) {
        validateRequiredFields(externalId, clientCode, sid, jti);

        return Jwts.builder()
                .header().keyId(activeKid).and()
                .subject(externalId)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_CLIENT, clientCode)
                .claim(CLAIM_SID, sid)
                .id(jti)
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(activePrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    private void validateRequiredFields(String externalId, String clientCode, String sid, String jti) {
        if (externalId == null || externalId.isBlank()) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (clientCode == null || clientCode.isBlank() || clientCode.length() > 64) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (sid == null || sid.isBlank() || sid.length() > 128) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (jti == null || jti.isBlank() || jti.length() > 128) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (externalId.length() > 256) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * 验证令牌并返回负载。
     * 校验顺序：长度 → jjwt 验签/过期/issuer/exp-iat → 类型匹配 → 禁止旧字段 → 黑名单。
     */
    private TokenPayload verify(String token, String expectType) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            if (header.getAlgorithm() == null || !"RS256".equals(header.getAlgorithm())) {
                                throw new BizException(ErrorCode.TOKEN_INVALID);
                            }
                            String kid = header.getKeyId();
                            if (kid == null || kid.isBlank() || !KID_PATTERN.matcher(kid).matches()) {
                                throw new BizException(ErrorCode.TOKEN_INVALID);
                            }
                            PublicKey key = publicKeyByKid.get(kid);
                            if (key == null) {
                                throw new BizException(ErrorCode.TOKEN_INVALID);
                            }
                            return key;
                        }
                    })
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (BizException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 验证失败: {}", e.getClass().getSimpleName());
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        try {
            validateClaims(claims, expectType);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT claims 校验失败: {}", e.getClass().getSimpleName());
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        String sid = claims.get(CLAIM_SID, String.class);
        try {
            if (blacklistService.isRevoked(sid, jti)) {
                throw new BizException(ErrorCode.TOKEN_REVOKED);
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("JWT 黑名单查询异常: {}", e.getClass().getSimpleName());
            throw e;
        }

        String externalId = claims.getSubject();
        String clientCode = claims.get(CLAIM_CLIENT, String.class);
        Instant expiresAt = claims.getExpiration().toInstant();
        return new TokenPayload(jti, externalId, clientCode, sid, expiresAt);
    }

    private void validateClaims(Claims claims, String expectType) {
        if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getSubject().length() > 256) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (claims.getIssuer() == null || !claims.getIssuer().equals(properties.getIssuer())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (claims.getIssuedAt() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (claims.getExpiration() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration().toInstant();
        if (!expiresAt.isAfter(issuedAt)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        if (issuedAt.isAfter(clock.instant())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectType.equals(type)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        String clientCode = claims.get(CLAIM_CLIENT, String.class);
        String sid = claims.get(CLAIM_SID, String.class);
        String jti = claims.getId();
        validateRequiredFields(claims.getSubject(), clientCode, sid, jti);

        // 禁止旧版 uid/userId 字段
        if (claims.containsKey("uid") || claims.containsKey("userId")) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    private static PublicKey decodePublicKey(String base64, String kid) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名密钥 " + kid + " 公钥解析失败");
        }
    }

    private static PrivateKey decodePrivateKey(String base64, String kid) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名密钥 " + kid + " 私钥解析失败");
        }
    }

    private static void validateRsaKey(Key key, String kid) {
        if (!(key instanceof RSAKey rsaKey)) {
            throw new IllegalStateException("JWT 签名密钥 " + kid + " 不是 RSA 密钥");
        }
        if (rsaKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalStateException("JWT 签名密钥 " + kid + " RSA 长度不足 " + MIN_RSA_BITS + " bit");
        }
    }

    private static boolean isKeyPairMatching(PublicKey publicKey, PrivateKey privateKey) {
        try {
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            byte[] data = "yunanexus-key-pair-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            sig.update(data);
            byte[] signature = sig.sign();

            java.security.Signature verify = java.security.Signature.getInstance("SHA256withRSA");
            verify.initVerify(publicKey);
            verify.update(data);
            return verify.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
