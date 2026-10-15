package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.config.JwtProperties;
import cc.nanoic.yunanexus.auth.service.TokenBlacklistService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JwtTokenService} 单元测试。<p>
 *
 * 覆盖：签发/验证往返、多 kid 旧公钥验证新签、篡改、过期、类型互换、黑名单、
 * 未知/缺 kid、RS512/HS256/none 拒绝、缺 required claims、错 issuer、旧 uid 拒绝、
 * 重复 kid、active 私钥缺失、短 RSA、错公私钥。
 *
 * @author yunanexus
 */
class JwtTokenServiceTest {

    private static final String KID_V1 = "v1";
    private static final String KID_V2 = "v2";
    private static final String ACTIVE_KID = KID_V2;

    private static KeyPair pairV1;
    private static KeyPair pairV2;

    private AuthProperties properties;
    private TokenBlacklistService blacklistService;
    private JwtTokenService service;
    private Clock fixedClock;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pairV1 = generator.generateKeyPair();
        pairV2 = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setAccessTokenTtlSeconds(1800);
        properties.setRefreshTokenTtlSeconds(604800);
        properties.setIssuer("yunanexus");

        blacklistService = mock(TokenBlacklistService.class);
        fixedClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(), pairV2.getPrivate(), ACTIVE_KID);
        service = new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock);
    }

    private static JwtProperties buildJwtProperties(PublicKey pubV1, PublicKey pubV2,
                                                    PrivateKey privV2, String activeKid) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid(activeKid);

        JwtProperties.SigningKey v1 = new JwtProperties.SigningKey();
        v1.setKid(KID_V1);
        v1.setPublicKeyBase64(Base64.getEncoder().encodeToString(pubV1.getEncoded()));

        JwtProperties.SigningKey v2 = new JwtProperties.SigningKey();
        v2.setKid(KID_V2);
        v2.setPublicKeyBase64(Base64.getEncoder().encodeToString(pubV2.getEncoded()));
        v2.setPrivateKeyBase64(Base64.getEncoder().encodeToString(privV2.getEncoded()));

        jwtProperties.setKeys(List.of(v1, v2));
        return jwtProperties;
    }

    @Test
    void 访问令牌签发与验证往返且无内部ID字段() {
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-1");
        assertNotNull(token);

        Claims claims = parseClaimsWithPublicKey(token, pairV2.getPublic());
        assertEquals("ext-10001", claims.getSubject());
        assertEquals("access", claims.get("typ", String.class));
        assertEquals("web", claims.get("client", String.class));
        assertEquals("sid-1", claims.get("sid", String.class));
        assertEquals("jti-1", claims.getId());
        assertEquals("yunanexus", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertEquals(null, claims.get("uid"));
        assertEquals(null, claims.get("userId"));
        assertEquals(null, claims.get("ext"));

        TokenService.TokenPayload payload = service.verifyAccessToken(token);
        assertEquals("jti-1", payload.jti());
        assertEquals("ext-10001", payload.externalId());
        assertEquals("web", payload.clientCode());
        assertEquals("sid-1", payload.sid());
    }

    @Test
    void 刷新令牌签发与验证往返且sub为外部ID() {
        String token = service.issueRefreshToken("ext-10001", "web", "sid-1", "jti-2");
        Claims claims = parseClaimsWithPublicKey(token, pairV2.getPublic());
        assertEquals("ext-10001", claims.getSubject());
        assertEquals("refresh", claims.get("typ", String.class));

        TokenService.TokenPayload payload = service.verifyRefreshToken(token);
        assertEquals("jti-2", payload.jti());
        assertEquals("ext-10001", payload.externalId());
        assertEquals("web", payload.clientCode());
        assertEquals("sid-1", payload.sid());
    }

    @Test
    void issueTokenPair返回元数据与公钥解析exp完全一致且无内部ID() {
        TokenService.TokenPair pair = service.issueTokenPair("ext-10001", "web", "sid-1", "jti-access", "jti-refresh");
        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());

        Claims accessClaims = parseClaimsWithPublicKey(pair.accessToken(), pairV2.getPublic());
        Claims refreshClaims = parseClaimsWithPublicKey(pair.refreshToken(), pairV2.getPublic());

        assertEquals(pair.accessExpiresAt(), accessClaims.getExpiration().toInstant());
        assertEquals(pair.refreshExpiresAt(), refreshClaims.getExpiration().toInstant());
        assertEquals("jti-access", accessClaims.getId());
        assertEquals("jti-refresh", refreshClaims.getId());
        assertEquals("access", accessClaims.get("typ", String.class));
        assertEquals("refresh", refreshClaims.get("typ", String.class));
        assertEquals(null, accessClaims.get("uid"));
        assertEquals(null, accessClaims.get("userId"));
        assertEquals(null, refreshClaims.get("uid"));
        assertEquals(null, refreshClaims.get("userId"));
    }

    @Test
    void 多kid旧公钥可验证新签activeKid令牌() {
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-3");
        // 使用 v2 公钥验签（activeKid）
        Claims claims = parseClaimsWithPublicKey(token, pairV2.getPublic());
        assertEquals("ext-10001", claims.getSubject());

        // 使用 v1 公钥验证应失败（签名由 v2 私钥生成）
        assertThrows(Exception.class, () -> parseClaimsWithPublicKey(token, pairV1.getPublic()));

        // 验证服务端也可正常验证
        TokenService.TokenPayload payload = service.verifyAccessToken(token);
        assertEquals("ext-10001", payload.externalId());
    }

    @Test
    void 移除旧kid后旧kid令牌被拒绝() {
        // 用 v1 私钥签发旧令牌
        String oldToken = Jwts.builder()
                .header().keyId(KID_V1).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-old")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV1.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // 当前服务只配置 v1 公钥，不配置 v1 私钥（activeKid=v2），应能验证 v1 签发的令牌
        TokenService.TokenPayload payload = service.verifyAccessToken(oldToken);
        assertEquals("jti-old", payload.jti());

        // 重新构建服务，移除 v1 公钥
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid(KID_V2);
        JwtProperties.SigningKey v2 = new JwtProperties.SigningKey();
        v2.setKid(KID_V2);
        v2.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV2.getPublic().getEncoded()));
        v2.setPrivateKeyBase64(Base64.getEncoder().encodeToString(pairV2.getPrivate().getEncoded()));
        jwtProperties.setKeys(List.of(v2));
        JwtTokenService serviceWithoutV1 = new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock);

        BizException e = assertThrows(BizException.class, () -> serviceWithoutV1.verifyAccessToken(oldToken));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 篡改令牌负载应被拒绝() {
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-4");
        String[] parts = token.split("\\.");
        // 篡改负载段中间字节，不依赖末尾 padding
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        payloadBytes[5] ^= 0x01;
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
        String tampered = String.join(".", parts);

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(tampered));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 合法令牌在exp恰好等于clock时被拒绝30102() {
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-exp-boundary");

        // 推进时钟恰好到令牌过期时间（TTL 1800 秒）
        Clock expClock = Clock.offset(fixedClock, java.time.Duration.ofSeconds(1800));
        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(), pairV2.getPrivate(), ACTIVE_KID);
        JwtTokenService expService = new JwtTokenService(properties, jwtProperties, blacklistService, expClock);

        BizException e = assertThrows(BizException.class, () -> expService.verifyAccessToken(token));
        assertEquals(30102, e.getCode());
    }

    @Test
    void 到期令牌应被拒绝() {
        Instant issueTime = fixedClock.instant();
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-expired");

        // 推进时钟到到期后
        Clock laterClock = Clock.offset(fixedClock, java.time.Duration.ofSeconds(1801));
        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(), pairV2.getPrivate(), ACTIVE_KID);
        JwtTokenService laterService = new JwtTokenService(properties, jwtProperties, blacklistService, laterClock);

        BizException e = assertThrows(BizException.class, () -> laterService.verifyAccessToken(token));
        assertEquals(30102, e.getCode());
    }

    @Test
    void 类型互换刷新令牌冒充访问令牌应被拒绝() {
        String refreshToken = service.issueRefreshToken("ext-10001", "web", "sid-1", "jti-5");
        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(refreshToken));
        assertEquals(30101, e.getCode());

        String accessToken = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-6");
        e = assertThrows(BizException.class, () -> service.verifyRefreshToken(accessToken));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 已吊销令牌应被拒绝() {
        when(blacklistService.isRevoked("sid-1", "jti-7")).thenReturn(true);
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-7");
        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30103, e.getCode());
    }

    @Test
    void 黑名单查询异常不应被吞掉() {
        RuntimeException expected = new RuntimeException("redis down");
        when(blacklistService.isRevoked("sid-1", "jti-8")).thenThrow(expected);
        String token = service.issueAccessToken("ext-10001", "web", "sid-1", "jti-8");
        RuntimeException actual = assertThrows(RuntimeException.class, () -> service.verifyAccessToken(token));
        assertEquals(expected, actual);
    }

    @Test
    void 未知kid令牌应被拒绝() {
        String token = Jwts.builder()
                .header().keyId("unknown-kid").and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-unknown")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 缺kid令牌应被拒绝() {
        String token = Jwts.builder()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-no-kid")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void RS512算法应被拒绝() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-rs512")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS512)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void HS256算法应被拒绝() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-hs256")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(Jwts.SIG.HS256.key().build(), Jwts.SIG.HS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void none算法应被拒绝() {
        // jjwt 不直接支持 none，手工构造无签名 JWT
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"kid\":\"v2\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"ext-10001\",\"typ\":\"access\",\"client\":\"web\",\"sid\":\"sid-1\",\"jti\":\"jti-none\",\"iss\":\"yunanexus\",\"iat\":1735689600,\"exp\":1735693200}".getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".";

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 缺requiredClaims应被拒绝() {
        // 缺 sid
        String tokenNoSid = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .id("jti-no-sid")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();
        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(tokenNoSid));
        assertEquals(30101, e.getCode());

        // 缺 exp
        String tokenNoExp = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-no-exp")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();
        e = assertThrows(BizException.class, () -> service.verifyAccessToken(tokenNoExp));
        assertEquals(30101, e.getCode());

        // 缺 iat
        String tokenNoIat = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-no-iat")
                .issuer("yunanexus")
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();
        e = assertThrows(BizException.class, () -> service.verifyAccessToken(tokenNoIat));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 错issuer应被拒绝() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-wrong-iss")
                .issuer("wrong-issuer")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 旧uid令牌应被拒绝() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-uid")
                .claim("uid", 10001L)
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 重复kid配置启动失败() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid(KID_V1);
        JwtProperties.SigningKey v1a = new JwtProperties.SigningKey();
        v1a.setKid(KID_V1);
        v1a.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV1.getPublic().getEncoded()));
        v1a.setPrivateKeyBase64(Base64.getEncoder().encodeToString(pairV1.getPrivate().getEncoded()));
        JwtProperties.SigningKey v1b = new JwtProperties.SigningKey();
        v1b.setKid(KID_V1);
        v1b.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV1.getPublic().getEncoded()));
        jwtProperties.setKeys(List.of(v1a, v1b));

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void activeKid私钥缺失启动失败() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid(KID_V2);
        JwtProperties.SigningKey v2 = new JwtProperties.SigningKey();
        v2.setKid(KID_V2);
        v2.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV2.getPublic().getEncoded()));
        jwtProperties.setKeys(List.of(v2));

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 短RSA密钥启动失败() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair shortPair = generator.generateKeyPair();

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid("short");
        JwtProperties.SigningKey sk = new JwtProperties.SigningKey();
        sk.setKid("short");
        sk.setPublicKeyBase64(Base64.getEncoder().encodeToString(shortPair.getPublic().getEncoded()));
        sk.setPrivateKeyBase64(Base64.getEncoder().encodeToString(shortPair.getPrivate().getEncoded()));
        jwtProperties.setKeys(List.of(sk));

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 错公私钥配置启动失败() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid("mismatch");
        JwtProperties.SigningKey sk = new JwtProperties.SigningKey();
        sk.setKid("mismatch");
        sk.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV1.getPublic().getEncoded()));
        sk.setPrivateKeyBase64(Base64.getEncoder().encodeToString(otherPair.getPrivate().getEncoded()));
        jwtProperties.setKeys(List.of(sk));

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 负TTL配置启动失败() {
        properties.setAccessTokenTtlSeconds(-1);
        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(),
                pairV2.getPrivate(), ACTIVE_KID);
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 未知activeKid配置启动失败() {
        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(),
                pairV2.getPrivate(), "unknown-active");
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 未来iat令牌应被拒绝() {
        Instant futureIat = fixedClock.instant().plusSeconds(60);
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-future-iat")
                .issuer("yunanexus")
                .issuedAt(Date.from(futureIat))
                .expiration(Date.from(futureIat.plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void exp不晚于iat应被拒绝() {
        Instant now = fixedClock.instant();
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-exp-eq-iat")
                .issuer("yunanexus")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 零TTL配置启动失败() {
        properties.setAccessTokenTtlSeconds(0);
        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(),
                pairV2.getPrivate(), ACTIVE_KID);
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 不合法base64公钥配置启动失败() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid("bad");
        JwtProperties.SigningKey sk = new JwtProperties.SigningKey();
        sk.setKid("bad");
        sk.setPublicKeyBase64("not-base64");
        sk.setPrivateKeyBase64(Base64.getEncoder().encodeToString(pairV2.getPrivate().getEncoded()));
        jwtProperties.setKeys(List.of(sk));

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 空keys配置启动失败() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid("v1");
        jwtProperties.setKeys(List.of());

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 缺activeKid配置启动失败() {
        JwtProperties jwtProperties = buildJwtProperties(pairV1.getPublic(), pairV2.getPublic(),
                pairV2.getPrivate(), null);

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    @Test
    void 正确签名但client类型错误映射30101() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", 12345L)
                .claim("sid", "sid-1")
                .id("jti-client-type")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 缺subJtiClient均拒绝() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .claim("typ", "access")
                .claim("sid", "sid-1")
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void userId残留拒绝() {
        String token = Jwts.builder()
                .header().keyId(ACTIVE_KID).and()
                .subject("ext-10001")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-userid")
                .claim("userId", 10001L)
                .issuer("yunanexus")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusSeconds(3600)))
                .signWith(pairV2.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BizException e = assertThrows(BizException.class, () -> service.verifyAccessToken(token));
        assertEquals(30101, e.getCode());
    }

    @Test
    void 非active私钥配置启动失败() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setActiveKid(KID_V2);

        JwtProperties.SigningKey v1 = new JwtProperties.SigningKey();
        v1.setKid(KID_V1);
        v1.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV1.getPublic().getEncoded()));
        v1.setPrivateKeyBase64(Base64.getEncoder().encodeToString(pairV1.getPrivate().getEncoded()));

        JwtProperties.SigningKey v2 = new JwtProperties.SigningKey();
        v2.setKid(KID_V2);
        v2.setPublicKeyBase64(Base64.getEncoder().encodeToString(pairV2.getPublic().getEncoded()));
        v2.setPrivateKeyBase64(Base64.getEncoder().encodeToString(pairV2.getPrivate().getEncoded()));

        jwtProperties.setKeys(List.of(v1, v2));

        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(properties, jwtProperties, blacklistService, fixedClock));
    }

    private Claims parseClaimsWithPublicKey(String token, PublicKey publicKey) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .clock(() -> Date.from(fixedClock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
