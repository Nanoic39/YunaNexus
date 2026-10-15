package cc.nanoic.yunanexus.gateway.security;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JwtVerifier} 单元测试。
 *
 * @author yunanexus
 */
class JwtVerifierTest {

    private static final String EXTERNAL_ID = "01234567890123456789012345678";
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    private static KeyPair keyPair;
    private static String publicKeyBase64;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private JwtVerifier verifier;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier(properties("v1", publicKeyBase64), clock);
    }

    @Test
    void validTokenVerifiesAndReturnsPayload() {
        String token = sign("access", "yunanexus", "v1", NOW, NOW.plusSeconds(600), false, true);

        TokenPayload payload = verifier.verify(token);

        assertEquals("jti-1", payload.jti());
        assertEquals(EXTERNAL_ID, payload.externalId());
        assertEquals("web", payload.clientCode());
        assertEquals("sid-1", payload.sid());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = sign("access", "yunanexus", "v1", NOW.minusSeconds(1200), NOW.minusSeconds(600), false, true);

        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify(token));
        assertEquals(401, ex.getHttpStatus());
        assertEquals(GatewayErrorCodes.TOKEN_EXPIRED, ex.getCode());
    }

    @Test
    void unknownKidIsRejected() {
        String token = sign("access", "yunanexus", "v2", NOW, NOW.plusSeconds(600), false, true);

        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify(token));
        assertEquals(GatewayErrorCodes.TOKEN_INVALID, ex.getCode());
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = sign("access", "other-issuer", "v1", NOW, NOW.plusSeconds(600), false, true);

        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify(token));
        assertEquals(GatewayErrorCodes.TOKEN_INVALID, ex.getCode());
    }

    @Test
    void refreshTokenTypeIsRejected() {
        String token = sign("refresh", "yunanexus", "v1", NOW, NOW.plusSeconds(600), false, true);

        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify(token));
        assertEquals(GatewayErrorCodes.TOKEN_INVALID, ex.getCode());
    }

    @Test
    void legacyUidClaimIsRejected() {
        String token = sign("access", "yunanexus", "v1", NOW, NOW.plusSeconds(600), true, true);

        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify(token));
        assertEquals(GatewayErrorCodes.TOKEN_INVALID, ex.getCode());
    }

    @Test
    void missingJtiIsRejected() {
        String token = sign("access", "yunanexus", "v1", NOW, NOW.plusSeconds(600), false, false);

        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify(token));
        assertEquals(GatewayErrorCodes.TOKEN_INVALID, ex.getCode());
    }

    @Test
    void blankTokenIsRejected() {
        GatewayAuthException ex = assertThrows(GatewayAuthException.class, () -> verifier.verify("  "));
        assertEquals(GatewayErrorCodes.TOKEN_INVALID, ex.getCode());
    }

    @Test
    void noKeysConfiguredFailsFast() {
        GatewayProperties props = new GatewayProperties();
        assertThrows(IllegalStateException.class, () -> new JwtVerifier(props, clock));
    }

    private static GatewayProperties properties(String kid, String publicKey) {
        GatewayProperties props = new GatewayProperties();
        props.setIssuer("yunanexus");
        GatewayProperties.Key key = new GatewayProperties.Key();
        key.setKid(kid);
        key.setPublicKeyBase64(publicKey);
        props.getJwt().setKeys(List.of(key));
        return props;
    }

    private static String sign(String type, String issuer, String kid,
                               Instant issuedAt, Instant expiresAt, boolean uid, boolean withJti) {
        var builder = Jwts.builder()
                .header().keyId(kid).and()
                .subject(EXTERNAL_ID)
                .claim("typ", type)
                .claim("client", "web")
                .claim("sid", "sid-1")
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if (withJti) {
            builder.id("jti-1");
        }
        if (uid) {
            builder.claim("uid", "1");
        }
        return builder.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
