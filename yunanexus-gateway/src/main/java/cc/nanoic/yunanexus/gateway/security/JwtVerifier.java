package cc.nanoic.yunanexus.gateway.security;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 网关 JWT 验签器（RS256，kid 选公钥）。<p>
 *
 * 仅使用公钥验签（私钥不出认证中心），校验：算法白名单 RS256、kid 合法性、
 * issuer、iat/exp 时序、令牌类型 {@code access}、必填字段，禁止旧版 {@code uid}/{@code userId}。
 * 不校验服务端会话存在性（由认证中心负责），网关仅额外做黑名单校验。
 *
 * @author yunanexus
 */
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private static final String TYPE_ACCESS = "access";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_CLIENT = "client";
    private static final String CLAIM_SID = "sid";
    private static final int MAX_TOKEN_LENGTH = 16384;
    private static final Pattern KID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final Map<String, PublicKey> publicKeyByKid;
    private final String issuer;
    private final Clock clock;

    public JwtVerifier(GatewayProperties properties, Clock clock) {
        this.issuer = properties.getIssuer();
        this.clock = clock;

        List<GatewayProperties.Key> keys = properties.getJwt().getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("未配置任何 JWT 公钥（yunanexus.gateway.jwt.keys）");
        }
        Map<String, PublicKey> map = new LinkedHashMap<>();
        for (GatewayProperties.Key key : keys) {
            if (key == null || key.getKid() == null || !KID_PATTERN.matcher(key.getKid()).matches()) {
                throw new IllegalStateException("JWT 公钥缺少或非法 kid");
            }
            if (key.getPublicKeyBase64() == null || key.getPublicKeyBase64().isBlank()) {
                // 未注入公钥的版本视为尚未启用，跳过（支持仅预置配置、按需注入多版本公钥）
                continue;
            }
            if (map.put(key.getKid(), decodePublicKey(key.getPublicKeyBase64(), key.getKid())) != null) {
                throw new IllegalStateException("JWT 公钥 kid 重复: " + key.getKid());
            }
        }
        if (map.isEmpty()) {
            throw new IllegalStateException("未配置任何有效 JWT 公钥（yunanexus.gateway.jwt.keys）");
        }
        this.publicKeyByKid = Map.copyOf(map);
    }

    /**
     * 验证 access 令牌。
     *
     * @param token 令牌字符串
     * @return 负载
     * @throws GatewayAuthException 验签失败（401 / 30101 / 30102）
     */
    public TokenPayload verify(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            if (header.getAlgorithm() == null || !"RS256".equals(header.getAlgorithm())) {
                                throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
                            }
                            String kid = header.getKeyId();
                            if (kid == null || !KID_PATTERN.matcher(kid).matches()) {
                                throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
                            }
                            PublicKey key = publicKeyByKid.get(kid);
                            if (key == null) {
                                throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
                            }
                            return key;
                        }
                    })
                    .clock(() -> java.util.Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_EXPIRED, "令牌已过期");
        } catch (GatewayAuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 验签失败: {}", e.getClass().getSimpleName());
            throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
        }

        validateClaims(claims);

        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
        }
        return new TokenPayload(
                jti,
                claims.getSubject(),
                claims.get(CLAIM_CLIENT, String.class),
                claims.get(CLAIM_SID, String.class),
                claims.getExpiration().toInstant());
    }

    private void validateClaims(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 256) {
            throw invalid();
        }
        if (claims.getIssuer() == null || !claims.getIssuer().equals(issuer)) {
            throw invalid();
        }
        if (claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw invalid();
        }
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration().toInstant();
        if (!expiresAt.isAfter(issuedAt)) {
            throw invalid();
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new GatewayAuthException(401, GatewayErrorCodes.TOKEN_EXPIRED, "令牌已过期");
        }
        if (issuedAt.isAfter(clock.instant())) {
            throw invalid();
        }
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw invalid();
        }
        String clientCode = claims.get(CLAIM_CLIENT, String.class);
        String sid = claims.get(CLAIM_SID, String.class);
        String jti = claims.getId();
        if (clientCode == null || clientCode.isBlank()
                || sid == null || sid.isBlank()
                || jti == null || jti.isBlank()) {
            throw invalid();
        }
        if (claims.containsKey("uid") || claims.containsKey("userId")) {
            throw invalid();
        }
    }

    private static GatewayAuthException invalid() {
        return new GatewayAuthException(401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
    }

    private static PublicKey decodePublicKey(String base64, String kid) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 公钥 " + kid + " 解析失败");
        }
    }
}
