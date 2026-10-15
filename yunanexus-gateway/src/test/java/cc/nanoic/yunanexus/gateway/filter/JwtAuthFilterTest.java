package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.common.core.util.SecureExternalIdGenerator;
import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import cc.nanoic.yunanexus.gateway.security.JwtVerifier;
import cc.nanoic.yunanexus.gateway.security.TokenBlacklistChecker;
import cc.nanoic.yunanexus.gateway.support.GatewayErrorWriter;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthFilter} 单元测试。
 *
 * @author yunanexus
 */
class JwtAuthFilterTest {

    private static final String AES_KEY = "2vcqxu6Um7UKKT3vkieJAQ==";
    private static final String HMAC_KEY = "kkSivnV1l47CKvKFtO250oVk/kiRPu4ZK5KmOpXOYEM=";
    private static final long INTERNAL_ID = 42L;
    private static final int SHARD_INDEX = 3;
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    private static KeyPair keyPair;
    private static String externalId;

    private JwtAuthFilter filter;
    private TokenBlacklistChecker blacklist;
    private AtomicReference<ServerWebExchange> captured;

    @BeforeAll
    static void setUpClass() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        SecureExternalIdGenerator idGenerator = new SecureExternalIdGenerator(
                List.of(new SecureExternalIdGenerator.KeyVersion(1, AES_KEY, HMAC_KEY)));
        externalId = idGenerator.encode(INTERNAL_ID, SHARD_INDEX);
    }

    @BeforeEach
    void setUp() {
        GatewayProperties props = new GatewayProperties();
        props.setIssuer("yunanexus");
        props.setPermitPaths(List.of("/api/v1/auth/login", "/actuator/**"));
        GatewayProperties.Key key = new GatewayProperties.Key();
        key.setKid("v1");
        key.setPublicKeyBase64(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        props.getJwt().setKeys(List.of(key));

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtVerifier verifier = new JwtVerifier(props, clock);
        SecureExternalIdGenerator idGenerator = new SecureExternalIdGenerator(
                List.of(new SecureExternalIdGenerator.KeyVersion(1, AES_KEY, HMAC_KEY)));
        blacklist = mock(TokenBlacklistChecker.class);
        filter = new JwtAuthFilter(verifier, blacklist, idGenerator, props, new GatewayErrorWriter(props));
        captured = new AtomicReference<>();
    }

    private GatewayFilterChain capturingChain() {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void permittedPathPassesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        filter.filter(exchange, capturingChain()).block();

        assertNotNull(captured.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void missingTokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());

        filter.filter(exchange, capturingChain()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertNull(captured.get());
    }

    @Test
    void nonBearerTokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc")
                        .build());

        filter.filter(exchange, capturingChain()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void validTokenInjectsIdentityHeaders() {
        when(blacklist.isRevoked("sid-1", "jti-1")).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .build());

        filter.filter(exchange, capturingChain()).block();

        assertNotNull(captured.get());
        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertEquals(Long.toString(INTERNAL_ID), headers.getFirst("X-User-Id"));
        assertEquals(Integer.toString(SHARD_INDEX), headers.getFirst("X-Shard-Index"));
    }

    @Test
    void clientSuppliedIdentityHeadersAreOverwritten() {
        when(blacklist.isRevoked("sid-1", "jti-1")).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .header("X-User-Id", "999")
                        .header("X-Shard-Index", "15")
                        .build());

        filter.filter(exchange, capturingChain()).block();

        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertEquals(Long.toString(INTERNAL_ID), headers.getFirst("X-User-Id"));
        assertEquals(Integer.toString(SHARD_INDEX), headers.getFirst("X-Shard-Index"));
    }

    @Test
    void permittedPathStripsIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .header("X-User-Id", "999")
                        .build());

        filter.filter(exchange, capturingChain()).block();

        assertNull(captured.get().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void revokedTokenReturns401WithRevokedCode() {
        when(blacklist.isRevoked("sid-1", "jti-1")).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .build());

        filter.filter(exchange, capturingChain()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body != null && body.contains("30103"));
        assertNull(captured.get());
    }

    @Test
    void invalidExternalIdReturns401() {
        when(blacklist.isRevoked("sid-1", "jti-1")).thenReturn(Mono.just(false));
        String token = Jwts.builder()
                .header().keyId("v1").and()
                .subject("not-a-valid-external-id")
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-1")
                .issuer("yunanexus")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, capturingChain()).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertNull(captured.get());
    }

    private static String validToken() {
        return Jwts.builder()
                .header().keyId("v1").and()
                .subject(externalId)
                .claim("typ", "access")
                .claim("client", "web")
                .claim("sid", "sid-1")
                .id("jti-1")
                .issuer("yunanexus")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
