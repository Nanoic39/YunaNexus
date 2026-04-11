package cc.nanoic.yunanexus.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cc.nanoic.yunanexus.user.mapper")
public class YunaNexusUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusUserApplication.class, args);
    }
}
