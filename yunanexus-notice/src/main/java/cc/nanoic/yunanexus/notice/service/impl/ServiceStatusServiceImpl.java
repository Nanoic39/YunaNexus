package cc.nanoic.yunanexus.notice.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.ServiceStatusSummary;
import cc.nanoic.yunanexus.notice.domain.ServiceStatusView;
import cc.nanoic.yunanexus.notice.service.ServiceStatusService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 复用各服务 Actuator 健康检查；单服务所有实例等权计算健康分数。
 * 按服务缓存 15 秒并合并并发请求，单个实例失败不会中断总查询。
 * 每日历史仅记录真实查询观测，进程重启后重新累计。
 *
 * @author yunanexus
 */
@Service
public class ServiceStatusServiceImpl implements ServiceStatusService {

    static final List<String> SERVICES = List.of("gateway", "auth", "user", "permission",
            "file", "notice", "audit", "meta", "analytics");
    private final DiscoveryClient discovery;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, CachedStatus> cache = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<LocalDate, DailySamples>> history = new ConcurrentHashMap<>();

    @Autowired
    public ServiceStatusServiceImpl(DiscoveryClient discovery, ObjectMapper mapper) {
        this(discovery, mapper, Clock.systemUTC(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
    }

    ServiceStatusServiceImpl(DiscoveryClient discovery, ObjectMapper mapper, Clock clock, HttpClient http) {
        this.discovery = discovery;
        this.mapper = mapper;
        this.clock = clock;
        this.http = http;
    }

    @Override
    public ServiceStatusView getStatus(String name) {
        String service = normalize(name);
        if (!SERVICES.contains(service)) {
            throw new BizException(ErrorCode.NOT_FOUND, "未知服务: " + name);
        }
        return snapshot(service).join();
    }

    @Override
    public ServiceStatusSummary getSummary() {
        // 先启动全部查询，避免逐服务串行等待超时。
        List<CompletableFuture<ServiceStatusView>> pending = SERVICES.stream().map(this::snapshot).toList();
        List<ServiceStatusView> entries = pending.stream().map(CompletableFuture::join).toList();
        double availability = entries.stream().mapToDouble(ServiceStatusView::availability).average().orElse(0);
        String status = entries.stream().allMatch(e -> "up".equals(e.status())) ? "up"
                : entries.stream().allMatch(e -> "down".equals(e.status())) ? "down" : "degraded";
        return new ServiceStatusSummary(status, round(availability), entries);
    }

    private CompletableFuture<ServiceStatusView> snapshot(String service) {
        return cache.compute(service, (key, old) -> {
            if (old != null && (!old.value().isDone() || clock.millis() - old.createdAt() < 15_000)) {
                return old;
            }
            CompletableFuture<ServiceStatusView> value = CompletableFuture.supplyAsync(() -> {
                List<ServiceInstance> instances = discovery.getInstances("yunanexus-" + key);
                List<CompletableFuture<Double>> probes = instances.stream()
                        .map(instance -> CompletableFuture.supplyAsync(() -> probe(instance), executor)).toList();
                return probes.stream().mapToDouble(CompletableFuture::join).average().orElse(0);
            }, executor).completeOnTimeout(0.0, 4, TimeUnit.SECONDS).exceptionally(error -> 0.0)
                    .thenApply(score -> record(key, score));
            return new CachedStatus(clock.millis(), value);
        }).value();
    }

    private double probe(ServiceInstance instance) {
        try {
            String port = instance.getMetadata().get("management.port");
            // 缺少管理端口时不猜测业务端口，避免误报。
            if (port == null) return 0;
            URI uri = new URI(instance.isSecure() ? "https" : "http", null, instance.getHost(),
                    Integer.parseInt(port), "/actuator/health", null, null);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return 0;
            String status = mapper.readTree(response.body()).path("status").asText();
            return "UP".equals(status) ? 1 : "UNKNOWN".equals(status) ? 0.5 : 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private ServiceStatusView record(String service, double score) {
        TreeMap<LocalDate, DailySamples> days = history.computeIfAbsent(service, key -> new TreeMap<>());
        synchronized (days) {
            LocalDate today = LocalDate.now(clock);
            // 前端历史数组按连续日解释；漏采日期无法表示 unknown，故从断点重新累计。
            if (!days.isEmpty() && days.lastKey().isBefore(today.minusDays(1))) days.clear();
            DailySamples samples = days.computeIfAbsent(today, key -> new DailySamples());
            samples.total += score;
            samples.count++;
            days.headMap(today.minusDays(179)).clear();
            double total = days.values().stream().mapToDouble(day -> day.total).sum();
            long count = days.values().stream().mapToLong(day -> day.count).sum();
            return new ServiceStatusView("svc-" + service, "yunanexus-" + service, health(score),
                    round(total / count * 100), days.values().stream()
                    .map(day -> health(day.total / day.count)).toList());
        }
    }

    private static String normalize(String name) {
        if (name == null) return "";
        if (name.startsWith("yunanexus-")) return name.substring(10);
        if (name.startsWith("svc-")) return name.substring(4);
        return name;
    }

    private static String health(double score) {
        return score >= 1 ? "up" : score <= 0 ? "down" : "degraded";
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        http.close();
    }

    private record CachedStatus(long createdAt, CompletableFuture<ServiceStatusView> value) { }

    private static class DailySamples {
        private double total;
        private long count;
    }
}
