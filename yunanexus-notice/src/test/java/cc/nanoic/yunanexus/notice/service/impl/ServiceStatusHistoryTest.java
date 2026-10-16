package cc.nanoic.yunanexus.notice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class ServiceStatusHistoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final long now = Instant.parse("2026-10-16T12:10:00Z").getEpochSecond();

    @Test
    void gapsAreUnknownAndBucketsHaveExplicitUtcBoundaries() {
        var history = new ServiceStatusHistory(jdbc);
        var timeline = history.read("auth", now, 24);
        assertEquals(48, timeline.size());
        assertEquals("2026-10-16T12:00:00Z", timeline.getLast().startAt());
        assertEquals("2026-10-16T12:30:00Z", timeline.getLast().endAt());
        assertTrue(
                timeline.stream()
                        .allMatch(
                                p ->
                                        p.status().equals("unknown")
                                                && p.samples() == 0
                                                && p.availability() == null));
    }

    @Test
    void durableObservationsSurviveNewReaderAndAggregateWeightedScores() {
        long bucket = Math.floorDiv(now, 1800) * 1800;
        when(jdbc.queryForList(anyString(), eq("auth"), anyLong(), anyLong()))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "bucket_start",
                                        bucket,
                                        "score_sum",
                                        1.0,
                                        "sample_count",
                                        2L)));
        var first = new ServiceStatusHistory(jdbc).read("auth", now, 24).getLast();
        var second = new ServiceStatusHistory(jdbc).read("auth", now, 168).getLast();
        assertEquals("degraded", first.status());
        assertEquals(50.0, first.availability());
        assertEquals(2, second.samples());
        assertEquals(50.0, second.availability());
        assertEquals(56, new ServiceStatusHistory(jdbc).read("auth", now, 168).size());
        assertEquals(60, new ServiceStatusHistory(jdbc).read("auth", now, 720).size());
    }

    @Test
    void samplesUseFixedHalfHourKeyAndRetentionIsBounded() {
        var history = new ServiceStatusHistory(jdbc);
        history.record("auth", now, 0.5);
        verify(jdbc)
                .update(
                        contains("ON DUPLICATE KEY"),
                        eq("auth"),
                        eq(now / 1800 * 1800),
                        eq(0.5),
                        eq(0.5));
        history.prune(now);
        verify(jdbc).update(startsWith("DELETE"), eq(now - 31L * 86400));
    }
}
