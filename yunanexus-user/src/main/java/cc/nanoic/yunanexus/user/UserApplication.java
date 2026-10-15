package cc.nanoic.yunanexus.user;

import cc.nanoic.yunanexus.user.config.UserProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 用户中心启动类（roadmap 阶段 5）。<p>
 *
 * 用户资料（{@code sys_user}）按 {@code userId % 逻辑分片数} 路由到物理分库，
 * 对认证中心提供内部接口（创建资料 / 幂等回查）。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@MapperScan("cc.nanoic.yunanexus.user.mapper")
@EnableConfigurationProperties(UserProperties.class)
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
