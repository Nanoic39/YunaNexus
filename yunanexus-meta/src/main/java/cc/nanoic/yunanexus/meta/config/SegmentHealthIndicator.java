package cc.nanoic.yunanexus.meta.config;

import cc.nanoic.yunanexus.meta.service.IdSegmentService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 号段服务健康指示器。<p>
 *
 * 汇聚各 bizTag 的降级状态：任一 bizTag 进入降级（连续异步预加载失败）即视为不健康（DOWN），
 * 供 readiness 探针使用；liveness 探针不查依赖，不受影响（见 ADR-019）。
 *
 * @author yunanexus
 */
@Component
public class SegmentHealthIndicator implements HealthIndicator {

    private final IdSegmentService idSegmentService;

    public SegmentHealthIndicator(IdSegmentService idSegmentService) {
        this.idSegmentService = idSegmentService;
    }

    @Override
    public Health health() {
        Map<String, Boolean> degraded = idSegmentService.degradedStatus();
        List<String> degradedTags = degraded.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
        if (degradedTags.isEmpty()) {
            return Health.up().withDetail("degradedSegments", List.of()).build();
        }
        return Health.down().withDetail("degradedSegments", degradedTags).build();
    }
}