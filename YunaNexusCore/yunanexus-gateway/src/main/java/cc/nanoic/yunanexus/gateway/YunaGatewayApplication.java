package cc.nanoic.yunanexus.gateway;

import cc.nanoic.yunanexus.gateway.config.YunaGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(YunaGatewayProperties.class)
public class YunaGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaGatewayApplication.class, args);
    }
}
