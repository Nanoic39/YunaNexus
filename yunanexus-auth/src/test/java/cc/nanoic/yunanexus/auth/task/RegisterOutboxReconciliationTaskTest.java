package cc.nanoic.yunanexus.auth.task;

import cc.nanoic.yunanexus.auth.config.RegisterOutboxProperties;
import cc.nanoic.yunanexus.auth.domain.RegisterOutboxReconciliation;
import cc.nanoic.yunanexus.auth.mapper.AuthRegisterOutboxMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterOutboxReconciliationTaskTest {
    private static final String PREFIX = "yunanexus.auth.outbox.reconciliation";
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");
    private AuthRegisterOutboxMapper mapper;
    private RegisterOutboxProperties properties;
    private SimpleMeterRegistry registry;
    private RegisterOutboxReconciliationTask task;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthRegisterOutboxMapper.class);
        properties = new RegisterOutboxProperties();
        registry = new SimpleMeterRegistry();
        task = new RegisterOutboxReconciliationTask(mapper, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), registry);
    }

    @Test
    void beforeFirstRunMetricsAreUnknownAndDoNotQueryDatabase() {
        assertEquals(-1, count("pending"));
        assertEquals(-1, gauge(".last.run.success"));
        assertEquals(0, gauge(".last.success.timestamp.seconds"));
        assertEquals(-1, gauge(".alert"));
        verifyNoInteractions(mapper);
    }

    @Test
    void successfulRunPublishesAllCountsWithConfiguredCutoff() {
        properties.setReconciliationOverdueSeconds(3600);
        RegisterOutboxReconciliation result = new RegisterOutboxReconciliation();
        result.setPending(8);
        result.setFailed(2);
        result.setOverdue(3);
        result.setMissing(4);
        result.setOrphan(5);
        result.setMismatch(6);
        when(mapper.reconcile(any())).thenReturn(result);

        task.reconcile();

        verify(mapper).reconcile(LocalDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC));
        assertEquals(8, count("pending"));
        assertEquals(2, count("failed"));
        assertEquals(3, count("overdue"));
        assertEquals(4, count("missing"));
        assertEquals(5, count("orphan"));
        assertEquals(6, count("mismatch"));
        assertEquals(1, gauge(".last.run.success"));
        assertEquals(NOW.getEpochSecond(), gauge(".last.success.timestamp.seconds"));
        assertEquals(1, registry.get(PREFIX + ".runs").tag("result", "success").counter().count());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void failureRetainsLastSuccessfulSnapshotAndRecoveryReplacesIt() {
        RegisterOutboxReconciliation previous = new RegisterOutboxReconciliation();
        previous.setFailed(7);
        when(mapper.reconcile(any())).thenReturn(previous)
                .thenThrow(new IllegalStateException("sensitive connection details"))
                .thenReturn(new RegisterOutboxReconciliation());

        task.reconcile();
        assertDoesNotThrow(task::reconcile);
        assertEquals(7, count("failed"));
        assertEquals(0, gauge(".last.run.success"));
        assertEquals(NOW.getEpochSecond(), gauge(".last.success.timestamp.seconds"));
        assertEquals(1, registry.get(PREFIX + ".runs").tag("result", "failure").counter().count());

        task.reconcile();
        assertEquals(0, count("failed"));
        assertEquals(1, gauge(".last.run.success"));
        assertEquals(2, registry.get(PREFIX + ".runs").tag("result", "success").counter().count());
    }

    @Test
    void firstRunFailureDoesNotReportHealthyZeroCounts() {
        when(mapper.reconcile(any())).thenThrow(new IllegalStateException());
        task.reconcile();
        assertEquals(-1, count("failed"));
        assertEquals(0, gauge(".last.run.success"));
        assertEquals(0, gauge(".last.success.timestamp.seconds"));
    }

    @Test
    void nullSnapshotIsExecutionFailure() {
        task.reconcile();
        assertEquals(-1, count("pending"));
        assertEquals(0, gauge(".last.run.success"));
    }

    @Test
    void normalPendingRecordsAloneAreNotAnomalies() {
        RegisterOutboxReconciliation result = new RegisterOutboxReconciliation();
        result.setPending(100);
        assertFalse(result.hasAnomalies());
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "overdue", "missing", "orphan", "mismatch"})
    void eachProblemCategoryTriggersAnomaly(String kind) {
        RegisterOutboxReconciliation result = new RegisterOutboxReconciliation();
        switch (kind) {
            case "failed" -> result.setFailed(1);
            case "overdue" -> result.setOverdue(1);
            case "missing" -> result.setMissing(1);
            case "orphan" -> result.setOrphan(1);
            case "mismatch" -> result.setMismatch(1);
            default -> fail("unexpected kind");
        }
        assertTrue(result.hasAnomalies());
    }

    @Test
    void anomaliesRaiseAlertGaugeAndCounter() {
        RegisterOutboxReconciliation result = new RegisterOutboxReconciliation();
        result.setFailed(2);
        when(mapper.reconcile(any())).thenReturn(result);

        task.reconcile();

        assertEquals(1, gauge(".alert"));
        assertEquals(1, alertCount());
    }

    @Test
    void healthyRunClearsAlertGauge() {
        when(mapper.reconcile(any())).thenReturn(new RegisterOutboxReconciliation());

        task.reconcile();

        assertEquals(0, gauge(".alert"));
        assertEquals(0, alertCount());
    }

    @Test
    void executionFailureRetainsLastAlertState() {
        RegisterOutboxReconciliation anomaly = new RegisterOutboxReconciliation();
        anomaly.setMissing(1);
        when(mapper.reconcile(any())).thenReturn(anomaly)
                .thenThrow(new IllegalStateException("db down"));

        task.reconcile();
        assertEquals(1, gauge(".alert"));

        task.reconcile();
        // 执行失败不覆盖上次告警状态，避免掩盖已存在的异常
        assertEquals(1, gauge(".alert"));
        assertEquals(1, alertCount());
    }

    @Test
    void anomalyEmitsStableAlertLogMarker() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        try {
            RegisterOutboxReconciliation result = new RegisterOutboxReconciliation();
            result.setOverdue(1);
            when(mapper.reconcile(any())).thenReturn(result);

            task.reconcile();

            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getLevel() == Level.ERROR
                            && event.getFormattedMessage().contains("[ALERT]")));
        } finally {
            detachLogAppender(appender);
        }
    }

    @Test
    void healthyRunDoesNotEmitAlertLogMarker() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        try {
            when(mapper.reconcile(any())).thenReturn(new RegisterOutboxReconciliation());

            task.reconcile();

            assertTrue(appender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("[ALERT]")));
        } finally {
            detachLogAppender(appender);
        }
    }


    @Test
    void reconciliationIsOptInAndIndependentOfDelivery() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(AuthRegisterOutboxMapper.class, () -> mapper)
                .withBean(RegisterOutboxProperties.class, RegisterOutboxProperties::new)
                .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new)
                .withUserConfiguration(RegisterOutboxReconciliationTask.class);
        runner.run(context -> assertFalse(context.containsBean("registerOutboxReconciliationTask")));
        runner.withPropertyValues("yunanexus.auth.outbox.reconciliation-enabled=true",
                "yunanexus.auth.outbox.enabled=false")
                .run(context -> {
                    assertNotNull(context.getBean(RegisterOutboxReconciliationTask.class));
                    verifyNoInteractions(mapper);
                });
    }

    private double count(String kind) {
        return registry.get(PREFIX + ".records").tag("kind", kind).gauge().value();
    }

    private double gauge(String suffix) {
        return registry.get(PREFIX + suffix).gauge().value();
    }

    private double alertCount() {
        return registry.get(PREFIX + ".alerts").counter().count();
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(RegisterOutboxReconciliationTask.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(RegisterOutboxReconciliationTask.class)).detachAppender(appender);
    }
}

