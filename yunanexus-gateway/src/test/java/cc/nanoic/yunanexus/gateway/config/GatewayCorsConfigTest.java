package cc.nanoic.yunanexus.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GatewayCorsConfig} 单元测试：白名单禁止通配符。
 *
 * @author yunanexus
 */
class GatewayCorsConfigTest {

    private final GatewayCorsConfig config = new GatewayCorsConfig();

    @Test
    void wildcardOriginIsRejected() {
        GatewayProperties props = new GatewayProperties();
        props.setCorsAllowedOrigins(List.of("*"));
        assertThrows(IllegalStateException.class, () -> config.corsWebFilter(props));
    }

    @Test
    void blankOriginIsRejected() {
        GatewayProperties props = new GatewayProperties();
        props.setCorsAllowedOrigins(List.of("  "));
        assertThrows(IllegalStateException.class, () -> config.corsWebFilter(props));
    }

    @Test
    void emptyOriginsDisablesCorsButReturnsFilter() {
        assertNotNull(config.corsWebFilter(new GatewayProperties()));
    }

    @Test
    void explicitOriginsAccepted() {
        GatewayProperties props = new GatewayProperties();
        props.setCorsAllowedOrigins(List.of("http://localhost:5173"));
        assertNotNull(config.corsWebFilter(props));
    }
}
