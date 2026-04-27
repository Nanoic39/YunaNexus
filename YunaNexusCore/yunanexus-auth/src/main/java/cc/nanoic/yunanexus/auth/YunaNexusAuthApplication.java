package cc.nanoic.yunanexus.auth;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cc.nanoic.yunanexus.auth.mapper")
public class YunaNexusAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunaNexusAuthApplication.class, args);
    }
}
