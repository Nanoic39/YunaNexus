package cc.nanoic.yunanexus.meta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
public class MetaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetaApplication.class, args);
    }
}