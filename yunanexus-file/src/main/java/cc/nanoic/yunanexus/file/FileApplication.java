package cc.nanoic.yunanexus.file;

import cc.nanoic.yunanexus.file.config.FileProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件中心启动类（roadmap 阶段 7）。<p>
 *
 * 文件元数据（{@code file_meta}）按 {@code fileId % 逻辑分片数} 路由到物理分库，
 * 文件内容经 {@code FileStorage} 抽象落本地磁盘（预留 MinIO/OSS 扩展）。
 * 列表查询叠加数据权限（{@code @DataScope}），作为 L4 行级权限的落地试点。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@MapperScan("cc.nanoic.yunanexus.file.mapper")
@EnableConfigurationProperties(FileProperties.class)
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.file.client")
@EnableScheduling
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
