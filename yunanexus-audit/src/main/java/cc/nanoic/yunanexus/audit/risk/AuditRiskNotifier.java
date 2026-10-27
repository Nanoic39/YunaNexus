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
    private final AuditProperties.Qq qq;
    private final boolean enabled;
    private final long windowMillis;
    /** 规则名 → 上次发送时间（毫秒） */
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public AuditRiskNotifier(AuditProperties properties) {
        AuditProperties.Risk risk = properties.getRisk();
        this.enabled = risk.isEnabled();
        this.webhookUrl = risk.getWebhookUrl();
        this.qq = risk.getQq();
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
        if (qq != null && qq.isEnabled() && qq.getBaseUrl() != null && !qq.getBaseUrl().isBlank()) {
            sendQq(toText(risk, entity));
            return;
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            // 无可用通道时降级为结构化日志，绝不静默丢弃
            log.warn("{} level={} rule={} user={} uri={} reason={}", MARKER, risk.level(),
                    risk.rule(), entity.getUsername(), entity.getUri(), risk.reason());
            return;
        }
        send(toJson(risk, entity));
    }

    /**
     * 通过 OneBot 11 HTTP API 发送到 QQ（群或私聊）。<p>
     *
     * 选 OneBot 而不是官方 QQ 开放平台的原因：服务器上已有 NapCat，
     * 走 OneBot 不需要公网回调、不需要过审，告警能立刻可用；
     * 官方 API 的适配位可以通过再实现一个通道补上，不影响本类。
     */
    private void sendQq(String text) {
        String base = qq.getBaseUrl().endsWith("/")
                ? qq.getBaseUrl().substring(0, qq.getBaseUrl().length() - 1)
                : qq.getBaseUrl();
        boolean toGroup = qq.getGroupId() != null && !qq.getGroupId().isBlank();
        String action = toGroup ? "/send_group_msg" : "/send_private_msg";
        String targetKey = toGroup ? "group_id" : "user_id";
        String targetValue = toGroup ? qq.getGroupId() : qq.getUserId();
        String body = "{\"" + targetKey + "\":" + quoteIfNumeric(targetValue)
                + ",\"message\":\"" + escape(text) + "\""
                + (toGroup && qq.isAtAll() ? ",\"auto_escape\":false" : "")
                + "}";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + action))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (qq.getAccessToken() != null && !qq.getAccessToken().isBlank()) {
            builder.header("Authorization", "Bearer " + qq.getAccessToken());
        }
        try {
            client.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.warn("{} QQ 投递失败：{}", MARKER, error.toString());
                        } else if (response.statusCode() >= 300) {
                            log.warn("{} QQ 投递被拒：status={}", MARKER, response.statusCode());
                        }
                    });
        } catch (RuntimeException error) {
            log.warn("{} QQ 投递异常：{}", MARKER, error.toString());
        }
    }

    /** 数字串不加引号（OneBot 要求 int），非数字加引号兜底 */
    private static String quoteIfNumeric(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }
        return value.chars().allMatch(Character::isDigit) ? value : "\"" + escape(value) + "\"";
    }

    /** 面向 QQ 的可读文案：先看级别与原因，再看谁做的，不用解析 JSON */
    private String toText(AuditRiskClassifier.Risk risk, AuditLog entity) {
        String icon = risk.level() == AuditRiskClassifier.Level.CRITICAL ? "[严重]" : "[高危]";
        return icon + " YunaNexus 审计告警\n"
                + "规则：" + risk.rule() + "\n"
                + "原因：" + risk.reason() + "\n"
                + "操作人：" + safeText(entity.getUsername()) + " (id=" + entity.getUserId() + ")\n"
                + "服务/模块：" + safeText(entity.getService()) + "/" + safeText(entity.getModule()) + "\n"
                + "动作：" + safeText(entity.getMethod()) + " " + safeText(entity.getUri()) + "\n"
                + "IP：" + safeText(entity.getIp()) + "\n"
                + "审计ID：" + entity.getId() + " trace=" + safeText(entity.getTraceId());
    }

    private static String safeText(String value) {
        return value == null ? "-" : value;
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
