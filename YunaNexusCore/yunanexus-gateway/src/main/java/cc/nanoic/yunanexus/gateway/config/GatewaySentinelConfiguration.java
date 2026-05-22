package cc.nanoic.yunanexus.gateway.config;

import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import com.alibaba.cloud.sentinel.SentinelProperties;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class GatewaySentinelConfiguration {
    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    @Resource
    private YunaGatewayProperties properties;

    @Resource
    private SentinelProperties sentinelProperties;

    public GatewaySentinelConfiguration(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    @PostConstruct
    public void initRules() {
        GatewayRuleManager.loadRules(properties.getSentinel().getFlowRules().stream()
                .map(rule -> new GatewayFlowRule(rule.getResource())
                        .setCount(rule.getCount())
                        .setIntervalSec(rule.getIntervalSec())
                        .setBurst(rule.getBurst()))
                .collect(Collectors.toSet()));

        Set<ApiDefinition> apiDefinitions = new HashSet<>();
        for (YunaGatewayProperties.ApiDefinitionProperty definition : properties.getSentinel().getApiDefinitions()) {
            if (definition.getPatterns().isEmpty()) {
                continue;
            }
            Set<ApiPredicateItem> items = definition.getPatterns().stream()
                    .map(pattern -> new ApiPathPredicateItem().setPattern(pattern))
                    .collect(Collectors.toSet());
            apiDefinitions.add(new ApiDefinition(definition.getApiName()).setPredicateItems(items));
        }
        GatewayApiDefinitionManager.loadApiDefinitions(apiDefinitions);

        GatewayCallbackManager.setBlockHandler(createBlockRequestHandler());
    }

    @Bean
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    @Bean
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    private BlockRequestHandler createBlockRequestHandler() {
        return (exchange, throwable) -> ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.fail(R.REQ_GLOBAL_LIMIT, "请求频率过快，请稍后重试"));
    }

    @Bean
    public Ordered sentinelGatewayFilterOrder() {
        sentinelProperties.getFilter().setEnabled(true);
        return () -> -100;
    }
}
