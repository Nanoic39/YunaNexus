package cc.nanoic.yunanexus.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.user.client")
@MapperScan("cc.nanoic.yunanexus.user.mapper")
@EnableScheduling
public class YunaNexusUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusUserApplication.class, args);
    }
}
