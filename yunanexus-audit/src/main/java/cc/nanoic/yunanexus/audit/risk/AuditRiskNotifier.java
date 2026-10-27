package cc.nanoic.yunanexus.audit.risk;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import cc.nanoic.yunanexus.audit.entity.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高危动作实时投递。<p>
 *
 * 三条刻意的克制：
 * <ol>
 *   <li><b>不阻塞审计写入</b>：投递走独立线程池语义（HttpClient 异步），失败只记日志。
 *       审计是"主业务"，不能被通知链路拖死——这条顺序不能反。</li>
 *   <li><b>按规则限流</b>：同一规则在窗口期内只发一次。刷屏的告警等于没有告警，
 *       这是 D10 那 42 条规则反而不被看的直接原因。</li>
 *   <li><b>未配置 webhook 时降级为结构化日志</b>：不静默失败，
 *       日志里带 {@code AUDIT_RISK} 标记，可被日志采集侧兜住。</li>
 * </ol>
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class AuditRiskNotifier {

    private static final String MARKER = "AUDIT_RISK";

    private final HttpClient client;
    private final String webhookUrl;
    private final boolean enabled;
    private final long windowMillis;
    /** 规则名 → 上次发送时间（毫秒） */
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public AuditRiskNotifier(AuditProperties properties) {
        AuditProperties.Risk risk = properties.getRisk();
        this.enabled = risk.isEnabled();
        this.webhookUrl = risk.getWebhookUrl();
        this.windowMillis = Math.max(0L, risk.getWindowSeconds()) * 1000L;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, risk.getTimeoutMillis())))
                .build();
    }

    /**
     * 判定并投递（命中才发）。
     *
     * @param entity 已落库的审计记录
     */
    public void notifyIfRisky(AuditLog entity) {
        if (!enabled) {
            return;
        }
        AuditRiskClassifier.Risk risk = AuditRiskClassifier.classify(entity);
        if (risk.level() == AuditRiskClassifier.Level.NONE) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSent.get(risk.rule());
        if (previous != null && now - previous < windowMillis) {
            return;
        }
        lastSent.put(risk.rule(), now);
        String payload = toJson(risk, entity);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("{} level={} rule={} user={} uri={} reason={}", MARKER, risk.level(),
                    risk.rule(), entity.getUsername(), entity.getUri(), risk.reason());
            return;
        }
        send(payload);
    }

    private void send(String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.warn("{} 投递失败：{}", MARKER, error.toString());
                        } else if (response.statusCode() >= 300) {
                            log.warn("{} 投递被拒：status={}", MARKER, response.statusCode());
                        }
                    });
        } catch (RuntimeException error) {
            // URL 非法等配置错误：只记日志，绝不冒泡影响审计写入
            log.warn("{} 投递异常：{}", MARKER, error.toString());
        }
    }

    /** 手工拼 JSON：字段固定可控，避免为一条告警引入序列化依赖差异 */
    private String toJson(AuditRiskClassifier.Risk risk, AuditLog entity) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        append(json, "source", "yunanexus-audit", true);
        append(json, "level", risk.level().name(), false);
        append(json, "rule", risk.rule(), false);
        append(json, "reason", risk.reason(), false);
        append(json, "userId", entity.getUserId() == null ? "" : String.valueOf(entity.getUserId()), false);
        append(json, "username", entity.getUsername(), false);
        append(json, "service", entity.getService(), false);
        append(json, "module", entity.getModule(), false);
        append(json, "action", entity.getAction(), false);
        append(json, "uri", entity.getUri(), false);
        append(json, "ip", entity.getIp(), false);
        append(json, "auditId", entity.getId() == null ? "" : String.valueOf(entity.getId()), false);
        append(json, "traceId", entity.getTraceId(), false);
        json.append('}');
        return json.toString();
    }

    private void append(StringBuilder json, String key, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
