package cc.nanoic.yunanexus.user;

import cc.nanoic.yunanexus.common.web.common.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.user.client")
@MapperScan("cc.nanoic.yunanexus.user.mapper")
@EnableScheduling
@Import(GlobalExceptionHandler.class)
public class YunaNexusUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusUserApplication.class, args);
    }
}
