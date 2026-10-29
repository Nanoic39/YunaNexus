package cc.nanoic.yunanexus.analytics.store;

import cc.nanoic.yunanexus.analytics.domain.TrackEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * MySQL 实现。
 *
 * <p>用 {@link JdbcTemplate} 而不是 MyBatis：分析模块原本配置的是 ClickHouse 数据源，
 * 引入 MyBatis 会与既有数据源装配冲突；这里用独立数据源 + 原生 SQL，边界清晰。
 *
 * @author yunanexus
 */
@Slf4j
public class MysqlEventStore implements EventStore {

    private static final String INSERT_SQL = "INSERT IGNORE INTO track_event "
            + "(event_id, event_type, event_time, anonymous_id, user_id, session_id, device_id, "
            + "page_url, referrer, client_ip, user_agent, props) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

    private final JdbcTemplate jdbcTemplate;

    public MysqlEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean append(TrackEvent event, String ip) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    truncate(event.getEventId(), 64),
                    truncate(event.getEventType(), 64),
                    toUtc(event.getEventTime()),
                    truncate(event.getAnonymousId(), 64),
                    truncate(event.getUserId(), 64),
                    truncate(event.getSessionId(), 64),
                    truncate(event.getDeviceId(), 64),
                    truncate(event.getPageUrl(), 512),
                    truncate(event.getReferrer(), 512),
                    truncate(ip, 64),
                    truncate(event.getUserAgent(), 512),
                    event.getProps() == null ? null : String.valueOf(event.getProps()));
            // INSERT IGNORE 命中重复时为 0 行：事件已存在，视为成功
            return true;
        } catch (RuntimeException error) {
            // 单条失败不能中断整批，但必须让调用方知道没存进去
            log.warn("埋点写入失败 eventId={}: {}", event.getEventId(), error.toString());
            return false;
        }
    }

    @Override
    public int purgeBefore(LocalDateTime before, int limit) {
        Integer deleted = jdbcTemplate.update(
                "DELETE FROM track_event WHERE event_time < ? LIMIT " + Math.max(1, limit), before);
        return deleted == null ? 0 : deleted;
    }

    @Override
    public long countBetween(LocalDateTime from, LocalDateTime to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_event WHERE event_time >= ? AND event_time < ?",
                Long.class, from, to);
        return count == null ? 0L : count;
    }

    private LocalDateTime toUtc(Long epochMillis) {
        return epochMillis == null ? LocalDateTime.now(ZoneOffset.UTC)
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
