package cc.nanoic.yunanexus.gateway;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 网关服务启动类（roadmap 阶段 4）。<p>
 *
 * 统一入口：TraceId 透传 → JWT 验签 → 黑名单校验 → 外部 ID 解码注入 → 限流 → 路由。
 *
 * @author yunanexus
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
