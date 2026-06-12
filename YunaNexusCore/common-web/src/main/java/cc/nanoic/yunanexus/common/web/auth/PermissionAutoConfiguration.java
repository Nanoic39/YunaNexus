package cc.nanoic.yunanexus.common.web.auth;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

@AutoConfiguration
@EnableConfigurationProperties(WebProperties.class)
public class PermissionAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
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

    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    public PermissionFeignInterceptor permissionFeignInterceptor() {
        return new PermissionFeignInterceptor();
    }
}