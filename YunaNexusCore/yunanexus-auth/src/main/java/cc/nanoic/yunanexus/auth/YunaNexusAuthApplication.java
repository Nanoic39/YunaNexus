package cc.nanoic.yunanexus.auth;

import cc.nanoic.yunanexus.common.web.common.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("cc.nanoic.yunanexus.auth.mapper")
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.auth.client")
@Import(GlobalExceptionHandler.class)
public class YunaNexusAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusAuthApplication.class, args);
    }
}