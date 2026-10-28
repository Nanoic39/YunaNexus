package cc.nanoic.yunanexus.common.audit.client;

import cc.nanoic.yunanexus.common.audit.config.AuditClientProperties;
import cc.nanoic.yunanexus.common.audit.event.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于有界队列 + 单写线程的异步审计客户端。<p>
 *
 * 设计取舍（每一条都是为了"审计不能拖垮业务"）：
 * <ul>
 *   <li><b>有界队列 + 满则丢弃</b>：宁可丢审计也不能让业务线程阻塞。丢弃计数可观测，
 *       可以据此扩容或降采样，而不是让它悄悄积压到 OOM。</li>
 *   <li><b>单写线程批量发送</b>：把 N 次 HTTP 合并成 1 次，审计服务压力可控。</li>
 *   <li><b>指数退避重试</b>：审计服务滚动发布期间短暂不可用是常态，重试两次足够；
 *       重试耗尽的批次计入丢弃并落 WARN。</li>
 *   <li><b>失败不抛给调用方</b>：submit 只做入队，任何异常都在写线程内消化。</li>
 * </ul>
 *
 * @author yunanexus
 */
@Slf4j
public class AsyncHttpAuditClient implements AuditClient, AutoCloseable {

    private final AuditClientProperties properties;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<AuditEvent> queue;
    private final HttpClient httpClient;
    private final AtomicLong dropped = new AtomicLong();
    private final Thread worker;
    private volatile boolean running = true;

    public AsyncHttpAuditClient(AuditClientProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.queue = new ArrayBlockingQueue<>(Math.max(64, properties.getQueueCapacity()));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .build();
        this.worker = new Thread(this::loop, "audit-client");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void submit(AuditEvent event) {
        if (event == null) {
            return;
        }
        if (!queue.offer(event)) {
            long total = dropped.incrementAndGet();
            if (properties.isLogDropped() && total % 100 == 1) {
                log.warn("审计队列已满，累计丢弃 {} 条（queueCapacity={}）",
                        total, properties.getQueueCapacity());
            }
        }
    }

    @Override
    public int backlog() {
        return queue.size();
    }

    @Override
    public long dropped() {
        return dropped.get();
    }

    private void loop() {
        List<AuditEvent> batch = new ArrayList<>(properties.getBatchSize());
        while (running || !queue.isEmpty()) {
            try {
                AuditEvent first = queue.poll(properties.getFlushIntervalMillis(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, properties.getBatchSize() - 1);
                send(batch);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException error) {
                // 写线程任何异常都不能死掉
                log.warn("审计上报线程异常：{}", error.toString());
            }
        }
    }

    private void send(List<AuditEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(batch);
        } catch (Exception error) {
            log.warn("审计事件序列化失败：{}", error.toString());
            return;
        }
        // 服务端支持单条对象或数组：数组走批量接口，减少请求数
        String url = properties.getEndpoint();
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 300) {
                    return;
                }
                if (response.statusCode() >= 500) {
                    backoff(attempt);
                    continue;
                }
                // 4xx 属于数据问题，重试无意义
                log.warn("审计上报被拒绝 status={}，丢弃 {} 条", response.statusCode(), batch.size());
                dropped.addAndGet(batch.size());
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                backoff(attempt);
            }
        }
        long total = dropped.addAndGet(batch.size());
        log.warn("审计上报重试耗尽，丢弃 {} 条（累计 {}）", batch.size(), total);
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(1000L, 100L * (1L << attempt)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
    }
}
