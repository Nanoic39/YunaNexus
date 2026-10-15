package cc.nanoic.yunanexus.permission;

import cc.nanoic.yunanexus.permission.config.AdminProperties;
import cc.nanoic.yunanexus.permission.config.PermissionCacheProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 权限中心启动类（roadmap 阶段 6）。<p>
 *
 * RBAC（角色 / 权限 / 绑定）+ ABAC（策略）混合模型，对网关与各服务提供统一鉴权接口。
 * scanBasePackages 覆盖 {@code cc.nanoic.yunanexus}，用于加载 common-web 的
 * {@code GlobalExceptionHandler} 与 {@code TraceIdFilter}。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@MapperScan("cc.nanoic.yunanexus.permission.mapper")
@EnableConfigurationProperties({AdminProperties.class, PermissionCacheProperties.class})
public class PermissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PermissionApplication.class, args);
    }
}
