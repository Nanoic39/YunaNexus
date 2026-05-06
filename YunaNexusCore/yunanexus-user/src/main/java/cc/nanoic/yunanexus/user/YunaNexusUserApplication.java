package cc.nanoic.yunanexus.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.user.client")
@MapperScan("cc.nanoic.yunanexus.user.mapper")
public class YunaNexusUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusUserApplication.class, args);
    }
}
