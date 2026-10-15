package cc.nanoic.yunanexus.gateway.config;

import cc.nanoic.yunanexus.gateway.support.GatewayErrorWriter;
import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayRateLimitConfig} 单元测试：按路由 id 装载限流规则。
 *
 * @author yunanexus
 */
class GatewayRateLimitConfigTest {

    @AfterEach
    void resetRules() {
        GatewayRuleManager.loadRules(new HashSet<>());
    }

    @Test
    void loadsRouteDimensionRules() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setRules(List.of(rule("yunanexus-auth", 50.0)));

        new GatewayRateLimitConfig(props, new GatewayErrorWriter(props)).init();

        Set<GatewayFlowRule> rules = GatewayRuleManager.getRules();
        assertEquals(1, rules.size());
        GatewayFlowRule loaded = rules.iterator().next();
        assertEquals("yunanexus-auth", loaded.getResource());
        assertEquals(50.0, loaded.getCount());
        assertEquals(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID, loaded.getResourceMode());
    }

    @Test
    void blankResourceIsSkipped() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setRules(List.of(rule("  ", 10.0)));

        new GatewayRateLimitConfig(props, new GatewayErrorWriter(props)).init();

        assertTrue(GatewayRuleManager.getRules().isEmpty());
    }

    private static GatewayProperties.Rule rule(String resource, double count) {
        GatewayProperties.Rule rule = new GatewayProperties.Rule();
        rule.setResource(resource);
        rule.setCount(count);
        rule.setIntervalSeconds(1);
        return rule;
    }
}
