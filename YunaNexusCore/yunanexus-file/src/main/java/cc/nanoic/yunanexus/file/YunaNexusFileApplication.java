package cc.nanoic.yunanexus.file;

import cc.nanoic.yunanexus.common.web.common.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.file.client")
@MapperScan("cc.nanoic.yunanexus.file.mapper")
@Import(GlobalExceptionHandler.class)
public class YunaNexusFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusFileApplication.class, args);
    }
}