package cc.nanoic.yunanexus.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 认证中心启动类。<p>
 *
 * scanBasePackages 覆盖 {@code cc.nanoic.yunanexus}，用于加载 common-web 提供的
 * {@code GlobalExceptionHandler} 与 {@code TraceIdFilter} 等通用组件。
 * {@code @EnableFeignClients} 启用 OpenFeign（注册时调用 meta 取号，ADR-005）。
 * {@code @EnableScheduling} 启用注册 outbox 补偿任务（ADR-021）。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@MapperScan("cc.nanoic.yunanexus.auth.mapper")
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.auth.client")
@EnableScheduling
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
