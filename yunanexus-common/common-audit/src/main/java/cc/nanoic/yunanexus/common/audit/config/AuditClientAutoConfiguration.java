package cc.nanoic.yunanexus.common.audit.config;

import cc.nanoic.yunanexus.common.audit.client.AsyncHttpAuditClient;
import cc.nanoic.yunanexus.common.audit.client.AuditClient;
import cc.nanoic.yunanexus.common.audit.collect.AuditWebInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 审计采集自动配置。<p>
 *
 * 拆分两个层次：
 * <ul>
 *   <li>上报客户端：Servlet 与 WebFlux（网关）都需要，无条件注册；</li>
 *   <li>业务拦截器：只有 Servlet 应用需要，用 {@code @ConditionalOnWebApplication(SERVLET)}
 *       隔离，避免网关（WebFlux）因缺少 Servlet API 而启动失败。</li>
 * </ul>
 *
 * <p>默认启用，可通过 {@code yunanexus.audit.client.enabled=false} 关闭。
 *
 * @author yunanexus
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AuditClientProperties.class)
@ConditionalOnProperty(prefix = "yunanexus.audit.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditClientAutoConfiguration {

    /** 上报客户端：所有 Web 应用（含网关）共用 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AuditClient.class)
    public AuditClient auditClient(AuditClientProperties properties,
                                   org.springframework.beans.factory.ObjectProvider<ObjectMapper> objectMapperProvider) {
        log.info("审计采集已启用，上报端点 {}", properties.getEndpoint());
        // 有些服务（如 meta）没有引入 starter-json，拿不到 ObjectMapper bean；
        // 事件字段只有字符串与数字，自建一个即可，不强制所有服务都具备 Jackson 装配。
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new AsyncHttpAuditClient(properties, objectMapper);
    }

    /** 业务语义拦截器：仅 Servlet 应用注册 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class AuditInterceptorConfiguration implements WebMvcConfigurer {

        private final AuditClient client;
        private final AuditClientProperties properties;
        private final String serviceName;
        private final java.util.List<String> excludePaths;

        AuditInterceptorConfiguration(AuditClient client, AuditClientProperties properties,
                                      @Value("${spring.application.name:unknown}") String serviceName) {
            this.client = client;
            this.properties = properties;
            this.serviceName = serviceName;
            this.excludePaths = properties.getExcludePaths();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AuditWebInterceptor(client, properties, serviceName))
                    .addPathPatterns("/**")
                    .excludePathPatterns(excludePaths);
        }
    }
}
