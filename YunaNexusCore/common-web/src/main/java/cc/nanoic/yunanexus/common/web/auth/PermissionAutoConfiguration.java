package cc.nanoic.yunanexus.common.web.auth;

import cc.nanoic.yunanexus.common.web.endpoint.DynamicPermissionFilter;
import cc.nanoic.yunanexus.common.web.endpoint.EndpointReporter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;

@AutoConfiguration
@EnableConfigurationProperties(WebProperties.class)
@EnableAspectJAutoProxy
public class PermissionAutoConfiguration {

    @Bean
    public FilterRegistrationBean<PermissionFilter> permissionFilterRegistration(
            WebProperties webProperties, RedissonClient redissonClient) {
        byte[] jwtSecret = webProperties.getJwt().getSecret() != null
                ? webProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        FilterRegistrationBean<PermissionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PermissionFilter(jwtSecret, redissonClient, webProperties.getExcludePaths()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE + 1);
        return registration;
    }

    /**
     * 接口权限上报（需配置 spring.application.name）
     */
    @Bean
    @ConditionalOnProperty(name = "spring.application.name")
    public EndpointReporter endpointReporter(
            @Value("${spring.application.name}") String serviceName,
            RedissonClient redissonClient,
            RequestMappingHandlerMapping handlerMapping) {
        return new EndpointReporter(serviceName, redissonClient, handlerMapping);
    }

    /**
     * 动态接口权限过滤器（需配置 spring.application.name）
     * 在 PermissionFilter 之后执行（order = MIN_VALUE + 2）
     */
    @Bean
    @ConditionalOnProperty(name = "spring.application.name")
    public FilterRegistrationBean<DynamicPermissionFilter> dynamicPermissionFilterRegistration(
            EndpointReporter endpointReporter) {
        FilterRegistrationBean<DynamicPermissionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DynamicPermissionFilter(endpointReporter));
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE + 2);
        return registration;
    }

    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    public PermissionFeignInterceptor permissionFeignInterceptor() {
        return new PermissionFeignInterceptor();
    }

    @Bean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }
}