package cc.nanoic.yunanexus.meta.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 号段服务配置。<p>
 *
 * 注册 {@link IdSegmentProperties}（号段阈值配置）与 {@link Clock}
 * （供动态步长按消耗速率计算，便于单元测试注入可控时钟）。
 *
 * @author yunanexus
 */
@Configuration
@EnableConfigurationProperties(IdSegmentProperties.class)
public class IdSegmentConfig {

    /**
     * 系统时钟，动态步长速率计算使用；测试可替换为可控时钟。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}