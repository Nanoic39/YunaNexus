package cc.nanoic.yunanexus.file.config;

import cc.nanoic.yunanexus.file.scan.ClamAvScanner;
import cc.nanoic.yunanexus.file.scan.NoopScanner;
import cc.nanoic.yunanexus.file.scan.VirusScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扫描引擎装配。
 *
 * <p>引擎选型由 {@code yunanexus.file.scan.engine} 决定：{@code clamav} 走 clamd INSTREAM，
 * 其他值走 Noop（永报不可用）。不提供"静默通过"的默认实现——没接引擎就必须
 * 表现为"不可用"，由上层按 fail-open 标记 pending 并进入补扫队列。
 *
 * @author yunanexus
 */
@Slf4j
@Configuration
public class ScannerConfig {

    @Bean
    public VirusScanner virusScanner(FileProperties properties) {
        FileProperties.Scan scan = properties.getScan();
        if ("clamav".equalsIgnoreCase(scan.getEngine())) {
            log.info("病毒扫描引擎: clamav {}:{}", scan.getClamdHost(), scan.getClamdPort());
            return new ClamAvScanner(scan);
        }
        log.warn("病毒扫描引擎未启用（engine={}），所有上传将标记为待扫描", scan.getEngine());
        return new NoopScanner();
    }
}
