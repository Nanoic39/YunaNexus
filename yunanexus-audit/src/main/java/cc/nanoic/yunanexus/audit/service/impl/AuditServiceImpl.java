package cc.nanoic.yunanexus.audit.service.impl;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import cc.nanoic.yunanexus.audit.config.ShardContextHolder;
import cc.nanoic.yunanexus.audit.domain.AuditMessage;
import cc.nanoic.yunanexus.audit.domain.AuditReportRequest;
import cc.nanoic.yunanexus.audit.chain.AuditChainHasher;
import cc.nanoic.yunanexus.audit.chain.AuditChainVerifier;
import cc.nanoic.yunanexus.audit.domain.AuditQueryFilter;
import cc.nanoic.yunanexus.audit.entity.AuditLog;
import cc.nanoic.yunanexus.audit.risk.AuditRiskNotifier;
import cc.nanoic.yunanexus.audit.mapper.AuditLogMapper;
import cc.nanoic.yunanexus.audit.mq.AuditProducer;
import cc.nanoic.yunanexus.audit.service.AuditService;
import cc.nanoic.yunanexus.audit.shard.ShardRouter;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Comparator;
import java.util.List;

/**
 * 审计日志采集与查询服务实现（roadmap 8.2 / 8.3）。<p>
 *
 * 上报链路：校验 → 组装消息 → 投递 RocketMQ（异步）。<br>
 * 落库链路：MQ 消费者回调 → 按 {@code user_id} 决策逻辑分片 → 写入对应物理库。<br>
 * 查询链路：命中单分片直查；无分片键时逐物理库查询后在内存归并分页。
 * 字段按列宽截断，避免超长导致整批落库失败。
 *
 * @author yunanexus
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    /** 各列最大长度（与 {@code audit_log} DDL 对齐） */
    private static final int LEN_USERNAME = 64;
    private static final int LEN_SERVICE = 64;
    private static final int LEN_MODULE = 64;
    private static final int LEN_ACTION = 64;
    private static final int LEN_METHOD = 16;
    private static final int LEN_URI = 512;
    private static final int LEN_ERROR_MSG = 512;
    private static final int LEN_IP = 64;
    private static final int LEN_USER_AGENT = 512;
    private static final int LEN_TRACE_ID = 64;
    /** 大字段（JSON 文本）保留上限，防止消息体过大 */
    private static final int LEN_LARGE_TEXT = 16000;

    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 每页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 导出上限：一次最多吐多少行，防"一个空条件把分片库拖死" */
    private static final int MAX_EXPORT_ROWS = 10000;
    /** 导出时间格式 */
    private static final DateTimeFormatter CSV_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 链校验默认/最大条数 */
    private static final int DEFAULT_VERIFY_SIZE = 1000;
    private static final int MAX_VERIFY_SIZE = 5000;

    /** 链锁分段数（按 shardIndex 取模） */
    private static final int CHAIN_LOCK_STRIPES = 16;

    /** 查询结果排序：操作时间倒序，同一时间按主键倒序 */
    private static final Comparator<AuditLog> LOG_ORDER =
            Comparator.comparing(AuditLog::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AuditLog::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final AuditLogMapper auditLogMapper;
    private final AuditProducer auditProducer;
    private final ShardRouter shardRouter;
    private final AuditRiskNotifier riskNotifier;
    /** 物理库数量（跨分片查询时逐库扫描的范围上界） */
    private final int physicalCount;
    /**
     * 分片级链锁：同一分片的"读链头 → 算摘要 → 插入"必须是原子段，
     * 否则并发写入会读到同一个链头导致分叉。
     *
     * <p>注意：这是<b>单实例内</b>的保护。若将来审计消费者多实例部署，
     * 必须改为数据库级串行化（如对链头行 SELECT ... FOR UPDATE），
     * 这一点写在类注释里而不是悄悄忽略。
     */
    private final ReentrantLock[] chainLocks = new ReentrantLock[CHAIN_LOCK_STRIPES];

    public AuditServiceImpl(AuditLogMapper auditLogMapper,
                            AuditProducer auditProducer,
                            ShardRouter shardRouter,
                            AuditRiskNotifier riskNotifier,
                            AuditProperties properties) {
        this.auditLogMapper = auditLogMapper;
        this.auditProducer = auditProducer;
        this.shardRouter = shardRouter;
        this.riskNotifier = riskNotifier;
        this.physicalCount = properties.getShard().getPhysicalCount();
        for (int index = 0; index < chainLocks.length; index++) {
            chainLocks[index] = new ReentrantLock();
        }
    }

    @Override
    public void report(AuditReportRequest request) {
        AuditMessage message = toMessage(request);
        auditProducer.send(message);
        log.debug("审计日志已入队 userId={} service={} action={}",
                message.getUserId(), message.getService(), message.getAction());
    }

    @Override
    public void persist(AuditMessage message) {
        long userId = message.getUserId() == null ? 0L : message.getUserId();
        int shardIndex = shardRouter.route(userId);
        AuditLog entity = toEntity(message, userId, shardIndex);
        ShardContextHolder.set(shardIndex);
        ReentrantLock lock = chainLocks[Math.floorMod(shardIndex, CHAIN_LOCK_STRIPES)];
        lock.lock();
        try {
            if (mergeGatewayRecord(entity, shardIndex)) {
                return;
            }
            // 先取链头再算摘要：两步必须与插入同处一个临界区，否则并发写会分叉
            String head = auditLogMapper.selectHeadHash(shardIndex);
            String prevHash = (head == null || head.isEmpty()) ? AuditChainHasher.GENESIS : head;
            entity.setPrevHash(prevHash);
            entity.setRecordHash(AuditChainHasher.chainDigest(prevHash, entity));
            auditLogMapper.insert(entity);
            // 落库之后才通知：审计本身成功是前提；通知失败也不能影响审计
            riskNotifier.notifyIfRisky(entity);
        } finally {
            lock.unlock();
            ShardContextHolder.clear();
        }
    }

    /**
     * 网关兜底记录与业务语义记录的合并（方案 A+B 的 C 语义）。
     *
     * <p>同一 traceId 下只保留一条：业务记录带真实模块/动作，价值更高；
     * 网关记录只证明"这个请求发生过"，在有业务记录时是冗余的。
     *
     * @param entity     待写入记录
     * @param shardIndex 逻辑分片
     * @return true 表示该事件无需写入
     */
    private boolean mergeGatewayRecord(AuditLog entity, int shardIndex) {
        String traceId = entity.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            return false;
        }
        boolean gatewayRecord = "gateway".equalsIgnoreCase(entity.getModule());
        if (gatewayRecord) {
            return auditLogMapper.countSemanticByTraceId(shardIndex, traceId) > 0;
        }
        auditLogMapper.deleteGatewayByTraceId(shardIndex, traceId);
        return false;
    }

    @Override
    public ChainReport verifyIntegrity(Long userId, Long fromId, Long toId, int limit) {
        int max = limit <= 0 || limit > MAX_VERIFY_SIZE ? DEFAULT_VERIFY_SIZE : limit;
        long lower = fromId == null ? 0L : fromId;
        long upper = toId == null ? Long.MAX_VALUE : toId;
        int verified = 0;
        List<String> breaks = new ArrayList<>();
        for (int shardIndex : resolveShards(userId)) {
            ShardContextHolder.set(shardIndex);
            try {
                List<AuditLog> segment =
                        auditLogMapper.selectChainSegment(shardIndex, lower, upper, max);
                if (segment.isEmpty()) {
                    continue;
                }
                // 分段校验以首条记录自带的 prev_hash 为边界：区间外部无法验证，
                // 区间内部逐条验证；这是"分段可校验"与"不误报"之间的必要取舍。
                AuditChainVerifier.Result result =
                        AuditChainVerifier.verify(segment, segment.get(0).getPrevHash());
                verified += result.total();
                if (!result.intact()) {
                    breaks.add(shardIndex + ":" + result.brokenAt() + ":" + result.reason());
                    log.error("审计链断裂 shard={} id={} reason={}",
                            shardIndex, result.brokenAt(), result.reason());
                }
            } finally {
                ShardContextHolder.clear();
            }
        }
        return new ChainReport(verified, breaks.isEmpty(), List.copyOf(breaks));
    }

    @Override
    public PageResult<AuditLog> searchLogs(AuditQueryFilter filter, int page, int size) {
        AuditQueryFilter condition = filter == null ? new AuditQueryFilter() : filter.normalize();
        int p = normalizePage(page);
        int s = normalizeSize(size);
        int offset = (p - 1) * s;
        int window = Math.min(offset + s, MAX_EXPORT_ROWS);
        long total = 0L;
        List<AuditLog> merged = new ArrayList<>();
        for (int shardIndex : resolveShards(condition.getUserId())) {
            ShardContextHolder.set(shardIndex);
            try {
                total += auditLogMapper.countByFilter(condition);
                merged.addAll(auditLogMapper.selectByFilter(condition, window, 0));
            } finally {
                ShardContextHolder.clear();
            }
        }
        if (total == 0L) {
            return PageResult.empty(p, s);
        }
        merged.sort(LOG_ORDER);
        List<AuditLog> records = offset >= merged.size()
                ? List.of()
                : List.copyOf(merged.subList(offset, Math.min(window, merged.size())));
        return PageResult.of(records, total, p, s);
    }

    @Override
    public byte[] exportCsv(AuditQueryFilter filter) {
        AuditQueryFilter condition = filter == null ? new AuditQueryFilter() : filter.normalize();
        List<AuditLog> merged = new ArrayList<>();
        for (int shardIndex : resolveShards(condition.getUserId())) {
            ShardContextHolder.set(shardIndex);
            try {
                merged.addAll(auditLogMapper.selectByFilter(condition, MAX_EXPORT_ROWS, 0));
            } finally {
                ShardContextHolder.clear();
            }
        }
        merged.sort(LOG_ORDER);
        if (merged.size() > MAX_EXPORT_ROWS) {
            merged = merged.subList(0, MAX_EXPORT_ROWS);
        }
        StringBuilder csv = new StringBuilder(merged.size() * 160);
        // 带 BOM，避免 Excel 打开中文乱码
        csv.append('\uFEFF');
        csv.append("id,操作时间,操作人,用户ID,服务,模块,动作,方法,URI,结果,耗时(ms),IP,TraceId,摘要\n");
        for (AuditLog item : merged) {
            appendCsvRow(csv, item);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 写一行 CSV：所有字段按 RFC 4180 加引号并转义内部引号，杜绝逗号/换行注入 */
    private void appendCsvRow(StringBuilder csv, AuditLog item) {
        csv.append(item.getId()).append(',');
        csv.append(csvCell(item.getCreateTime() == null ? null
                : item.getCreateTime().format(CSV_TIME_FORMAT))).append(',');
        csv.append(csvCell(item.getUsername())).append(',');
        csv.append(csvCell(item.getUserId())).append(',');
        csv.append(csvCell(item.getService())).append(',');
        csv.append(csvCell(item.getModule())).append(',');
        csv.append(csvCell(item.getAction())).append(',');
        csv.append(csvCell(item.getMethod())).append(',');
        csv.append(csvCell(item.getUri())).append(',');
        csv.append(csvCell(item.getResult())).append(',');
        csv.append(csvCell(item.getDurationMs())).append(',');
        csv.append(csvCell(item.getIp())).append(',');
        csv.append(csvCell(item.getTraceId())).append(',');
        csv.append(csvCell(item.getRecordHash())).append('\n');
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    @Override
    public PageResult<AuditLog> queryLogs(Long userId, String service, Long startTime, Long endTime,
                                          int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        LocalDateTime start = toDateTime(startTime);
        LocalDateTime end = toDateTime(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ErrorCode.AUDIT_QUERY_RANGE_INVALID);
        }
        String svc = (service == null || service.isBlank()) ? null : service.trim();

        int offset = (p - 1) * s;
        int window = offset + s;
        long total = 0L;
        List<AuditLog> merged = new ArrayList<>();
        for (int shardIndex : resolveShards(userId)) {
            ShardContextHolder.set(shardIndex);
            try {
                total += auditLogMapper.countByCondition(userId, svc, start, end);
                merged.addAll(auditLogMapper.selectByCondition(userId, svc, start, end, window));
            } finally {
                ShardContextHolder.clear();
            }
        }
        if (total == 0L) {
            return PageResult.empty(p, s);
        }
        merged.sort(LOG_ORDER);
        List<AuditLog> records = offset >= merged.size()
                ? List.of()
                : List.copyOf(merged.subList(offset, Math.min(window, merged.size())));
        return PageResult.of(records, total, p, s);
    }

    /** 解析需扫描的分片：指定 userId 时命中单一逻辑分片，否则逐物理库全扫。 */
    private List<Integer> resolveShards(Long userId) {
        if (userId != null) {
            return List.of(shardRouter.route(userId));
        }
        List<Integer> all = new ArrayList<>(physicalCount);
        for (int index = 0; index < physicalCount; index++) {
            all.add(index);
        }
        return all;
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static LocalDateTime toDateTime(Long epochMillis) {
        return epochMillis == null
                ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    /** 上报请求 → MQ 消息（补默认状态与操作时间）。 */
    private AuditMessage toMessage(AuditReportRequest request) {
        AuditMessage message = new AuditMessage();
        message.setUserId(request.getUserId() == null ? 0L : request.getUserId());
        message.setUsername(request.getUsername());
        message.setService(request.getService());
        message.setModule(request.getModule());
        message.setAction(request.getAction());
        message.setMethod(request.getMethod());
        message.setUri(request.getUri());
        message.setParams(request.getParams());
        message.setResult(request.getResult());
        message.setStatus(request.getStatus() == null ? AuditLog.STATUS_SUCCESS : request.getStatus());
        message.setErrorMsg(request.getErrorMsg());
        message.setIp(request.getIp());
        message.setUserAgent(request.getUserAgent());
        message.setDurationMs(request.getDurationMs());
        message.setTraceId(request.getTraceId());
        message.setCreateTime(request.getCreateTime() == null
                ? System.currentTimeMillis() : request.getCreateTime());
        return message;
    }

    /** MQ 消息 → 落库实体（按列宽截断）。 */
    private AuditLog toEntity(AuditMessage message, long userId, int shardIndex) {
        AuditLog entity = new AuditLog();
        entity.setUserId(userId);
        entity.setShardIndex(shardIndex);
        entity.setUsername(truncate(message.getUsername(), LEN_USERNAME));
        entity.setService(truncate(message.getService(), LEN_SERVICE));
        entity.setModule(truncate(message.getModule(), LEN_MODULE));
        entity.setAction(truncate(message.getAction(), LEN_ACTION));
        entity.setMethod(truncate(message.getMethod(), LEN_METHOD));
        entity.setUri(truncate(message.getUri(), LEN_URI));
        entity.setParams(truncate(message.getParams(), LEN_LARGE_TEXT));
        entity.setResult(truncate(message.getResult(), LEN_LARGE_TEXT));
        entity.setStatus(message.getStatus() == null ? AuditLog.STATUS_SUCCESS : message.getStatus());
        entity.setErrorMsg(truncate(message.getErrorMsg(), LEN_ERROR_MSG));
        entity.setIp(truncate(message.getIp(), LEN_IP));
        entity.setUserAgent(truncate(message.getUserAgent(), LEN_USER_AGENT));
        entity.setDurationMs(message.getDurationMs());
        entity.setTraceId(truncate(message.getTraceId(), LEN_TRACE_ID));
        long millis = message.getCreateTime() == null ? System.currentTimeMillis() : message.getCreateTime();
        entity.setCreateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
        return entity;
    }

    /** 截断至列宽上限（null 原样返回）。 */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
