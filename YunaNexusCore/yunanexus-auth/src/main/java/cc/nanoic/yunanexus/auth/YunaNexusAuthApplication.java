package cc.nanoic.yunanexus.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("cc.nanoic.yunanexus.auth.mapper")
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.auth.client")
public class YunaNexusAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusAuthApplication.class, args);
    }
}
