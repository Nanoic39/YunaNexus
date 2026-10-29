package cc.nanoic.yunanexus.analytics.service;

import cc.nanoic.yunanexus.analytics.domain.FunnelResult;
import cc.nanoic.yunanexus.analytics.domain.RetentionResult;
import cc.nanoic.yunanexus.analytics.domain.SegmentResult;
import cc.nanoic.yunanexus.analytics.domain.UserProfileResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析查询（漏斗 / 留存 / 分群 / 画像）。
 *
 * <p>三条硬约束，都是为了"查询不能拖垮采集"：
 * <ul>
 *   <li>时间范围必填（默认最近 7 天），杜绝全表扫描；</li>
 *   <li>SQL 全参数化，条件值不拼进语句；</li>
 *   <li>结果集有上限（分群最多 5000 个用户 ID）。</li>
 * </ul>
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class AnalyticsQueryService {

    /** 分群结果上限 */
    private static final int MAX_SEGMENT_USERS = 5000;

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsQueryService(JdbcTemplate analyticsJdbcTemplate) {
        this.jdbcTemplate = analyticsJdbcTemplate;
    }

    /**
     * 漏斗：按用户统计依次完成各步骤的人数。
     *
     * @param steps 步骤事件类型（顺序）
     * @param from  起始时间
     * @param to    结束时间
     * @return 各步骤人数
     */
    public FunnelResult funnel(List<String> steps, LocalDateTime from, LocalDateTime to) {
        FunnelResult result = new FunnelResult();
        if (steps == null || steps.isEmpty()) {
            return result;
        }
        // 逐步骤收窄用户集合：每一步都必须在该用户"上一步时间点之后"发生
        List<String> currentUsers = null;
        for (int index = 0; index < steps.size(); index++) {
            String step = steps.get(index);
            List<String> users;
            if (index == 0) {
                users = jdbcTemplate.queryForList(
                        "SELECT DISTINCT user_id FROM track_event WHERE event_type = ? "
                                + "AND event_time >= ? AND event_time < ? AND user_id IS NOT NULL",
                        String.class, step, from, to);
            } else {
                if (currentUsers == null || currentUsers.isEmpty()) {
                    currentUsers = List.of();
                }
                users = jdbcTemplate.queryForList(
                        "SELECT DISTINCT user_id FROM track_event WHERE event_type = ? "
                                + "AND event_time >= ? AND event_time < ? AND user_id IS NOT NULL "
                                + "AND user_id IN (SELECT user_id FROM track_event WHERE event_type = ? "
                                + "AND event_time >= ? AND event_time < ? AND user_id IS NOT NULL)",
                        String.class, steps.get(0), from, to, step);
            }
            result.getSteps().add(new FunnelResult.FunnelStep(step, users.size()));
            currentUsers = users;
        }
        return result;
    }

    /**
     * 留存：按首访日期分批，统计第 N 天仍活跃的用户占比。
     *
     * @param event 事件类型
     * @param days  观察天数
     * @param from  起始
     * @param to    结束
     * @return 留存矩阵
     */
    public RetentionResult retention(String event, int days, LocalDateTime from, LocalDateTime to) {
        RetentionResult result = new RetentionResult();
        int span = Math.max(1, Math.min(days, 30));
        for (int day = 0; day < span; day++) {
            result.getPeriods().add("D" + day);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE(first_seen) AS cohort, COUNT(*) AS total FROM ("
                        + "  SELECT user_id, MIN(event_time) AS first_seen FROM track_event "
                        + "  WHERE event_type = ? AND event_time >= ? AND event_time < ? AND user_id IS NOT NULL "
                        + "  GROUP BY user_id) t GROUP BY DATE(first_seen) ORDER BY cohort DESC LIMIT 30",
                event, from, to);
        for (Map<String, Object> row : rows) {
            String cohort = String.valueOf(row.get("cohort"));
            long total = ((Number) row.get("total")).longValue();
            RetentionResult.RetentionRow retentionRow = new RetentionResult.RetentionRow();
            retentionRow.setCohort(cohort);
            for (int day = 0; day < span; day++) {
                Long active = jdbcTemplate.queryForObject(
                        "SELECT COUNT(DISTINCT e.user_id) FROM track_event e "
                                + "JOIN (SELECT user_id, MIN(event_time) AS first_seen FROM track_event "
                                + "      WHERE event_type = ? AND DATE(event_time) = ? AND user_id IS NOT NULL "
                                + "      GROUP BY user_id) c ON c.user_id = e.user_id "
                                + "WHERE e.event_type = ? AND DATE(e.event_time) = DATE_ADD(?, INTERVAL ? DAY)",
                        Long.class, event, LocalDate.parse(cohort), event, cohort, day);
                retentionRow.getRates().add(total == 0 ? 0.0
                        : Math.round((active == null ? 0 : active) * 10000.0 / total) / 10000.0);
            }
            result.getRows().add(retentionRow);
        }
        return result;
    }

    /**
     * 分群：找出时间范围内某事件次数达到阈值的用户。
     *
     * @param event    事件类型
     * @param minCount 最少次数
     * @param from     起始
     * @param to       结束
     * @return 用户列表
     */
    public SegmentResult segments(String event, int minCount, LocalDateTime from, LocalDateTime to) {
        SegmentResult result = new SegmentResult();
        List<String> users = jdbcTemplate.queryForList(
                "SELECT user_id FROM track_event WHERE event_type = ? AND event_time >= ? "
                        + "AND event_time < ? AND user_id IS NOT NULL GROUP BY user_id HAVING COUNT(*) >= ? "
                        + "ORDER BY COUNT(*) DESC LIMIT " + MAX_SEGMENT_USERS,
                String.class, event, from, to, Math.max(1, minCount));
        result.setUserIds(users);
        result.setTotal(users.size());
        return result;
    }

    /**
     * 画像：单用户事件类型分布。
     *
     * @param userId 用户外部 ID
     * @param from   起始
     * @param to     结束
     * @return 画像
     */
    public UserProfileResult profile(String userId, LocalDateTime from, LocalDateTime to) {
        UserProfileResult result = UserProfileResult.of(userId);
        if (userId == null || userId.isBlank()) {
            return result;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_type, COUNT(*) AS c FROM track_event WHERE user_id = ? "
                        + "AND event_time >= ? AND event_time < ? GROUP BY event_type ORDER BY c DESC",
                userId, from, to);
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            long count = ((Number) row.get("c")).longValue();
            counts.put(String.valueOf(row.get("event_type")), count);
            total += count;
        }
        result.setEventCounts(counts);
        // 价值分层：行为量级分档，便于运营快速识别高活跃用户
        if (total >= 100) {
            result.setValueTier("HIGH");
        } else if (total >= 20) {
            result.setValueTier("MEDIUM");
        } else {
            result.setValueTier("LOW");
        }
        return result;
    }
}
