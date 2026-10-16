package cc.nanoic.yunanexus.notice.service.impl;

import cc.nanoic.yunanexus.notice.domain.ServiceStatusPeriod;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bounded durable half-hour observations; no invented samples for missing periods. */
@Component
public class ServiceStatusHistory {
    private final JdbcTemplate jdbc;

    public ServiceStatusHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void initialize() {
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS notice_service_status_bucket (
                  service_name VARCHAR(32) NOT NULL,
                  bucket_start BIGINT NOT NULL,
                  score_sum DOUBLE NOT NULL,
                  sample_count BIGINT NOT NULL,
                  PRIMARY KEY(service_name, bucket_start)
                ) ENGINE=InnoDB
                """);
    }

    void record(String service, long now, double score) {
        long bucket = Math.floorDiv(now, 1800) * 1800;
        jdbc.update(
                """
                INSERT INTO notice_service_status_bucket VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE score_sum=score_sum+?, sample_count=sample_count+1
                """,
                service,
                bucket,
                score,
                score);
    }

    void prune(long now) {
        jdbc.update(
                "DELETE FROM notice_service_status_bucket WHERE bucket_start < ?",
                now - 31L * 86400);
    }

    List<ServiceStatusPeriod> read(String service, long now, int hours) {
        long step = hours == 24 ? 1800 : hours == 168 ? 10800 : 43200;
        long end = Math.floorDiv(now, step) * step + step;
        long start = end - hours * 3600L;
        var rows =
                jdbc.queryForList(
                        """
SELECT bucket_start, score_sum, sample_count FROM notice_service_status_bucket
WHERE service_name=? AND bucket_start>=? AND bucket_start<? ORDER BY bucket_start
""",
                        service,
                        start,
                        end);
        List<ServiceStatusPeriod> result = new ArrayList<>();
        for (long at = start; at < end; at += step) {
            double sum = 0;
            long count = 0;
            for (var row : rows) {
                long timestamp = ((Number) row.get("bucket_start")).longValue();
                if (timestamp >= at && timestamp < at + step) {
                    sum += ((Number) row.get("score_sum")).doubleValue();
                    count += ((Number) row.get("sample_count")).longValue();
                }
            }
            double score = count == 0 ? 0 : sum / count;
            result.add(
                    new ServiceStatusPeriod(
                            Instant.ofEpochSecond(at).toString(),
                            Instant.ofEpochSecond(at + step).toString(),
                            count == 0
                                    ? "unknown"
                                    : score >= 1 ? "up" : score <= 0 ? "down" : "degraded",
                            count == 0 ? null : Math.round(score * 10000) / 100.0,
                            count));
        }
        return result;
    }
}
