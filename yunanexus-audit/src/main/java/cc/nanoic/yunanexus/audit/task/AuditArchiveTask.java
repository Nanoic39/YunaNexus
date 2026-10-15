package cc.nanoic.yunanexus.audit.task;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import cc.nanoic.yunanexus.audit.config.ShardRoutingDataSource;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * 审计日志归档任务（roadmap 8.4）。<p>
 *
 * 逐个物理库对 {@code audit_log} 执行：<br>
 * 1. <b>滚动补齐未来分区</b>：为「当前月 ~ 当前月 + pre-create-months」中缺失的月份，
 * 通过 {@code REORGANIZE PARTITION pmax} 追加月度 RANGE 分区，避免新数据落入
 * {@code pmax} 兜底分区导致后续无法分离；<br>
 * 2. <b>导出并删除超期分区</b>：分区上界早于「保留起点（当前月首日 - retention-months）」
 * 的月度分区，先整分区导出为 gzip CSV 冷备至 {@code backup-dir/物理库名/}，
 * 导出失败则不执行删除（数据安全优先），成功后 {@code DROP PARTITION} 释放空间。<br>
 *
 * 任务受 {@code yunanexus.audit.archive.enabled} 开关控制，逐库容错：单库失败记录
 * {@link ErrorCode#AUDIT_EXPORT_FAILED} 语义日志后继续处理其余物理库。
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class AuditArchiveTask {

    private static final String TABLE = "audit_log";
    /** MAXVALUE 兜底分区名，滚动新增分区时将其拆分 */
    private static final String PMAX = "pmax";
    /** 月度分区名前缀（如 p202610） */
    private static final String PARTITION_PREFIX = "p";

    private static final DateTimeFormatter SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final List<DataSource> delegates;
    private final AuditProperties.Archive archive;

    public AuditArchiveTask(DataSource dataSource, AuditProperties properties) {
        this.delegates = dataSource instanceof ShardRoutingDataSource routing
                ? routing.getDelegates()
                : List.of(dataSource);
        this.archive = properties.getArchive();
    }

    /**
     * 按日执行归档（默认每日 03:30，可通过 {@code yunanexus.audit.archive.cron} 覆盖）。
     */
    @Scheduled(cron = "${yunanexus.audit.archive.cron:0 30 3 * * ?}")
    public void scheduledArchive() {
        archive();
    }

    /**
     * 执行一次归档：遍历全部物理库，滚动补齐分区并冷备删除超期分区。
     */
    public void archive() {
        if (!archive.isEnabled()) {
            log.debug("审计归档未启用（yunanexus.audit.archive.enabled=false），跳过");
            return;
        }
        LocalDate today = LocalDate.now();
        for (int index = 0; index < delegates.size(); index++) {
            try (Connection connection = delegates.get(index).getConnection()) {
                String catalog = connection.getCatalog();
                Set<String> existing = listPartitions(connection);
                addFuturePartitions(connection, catalog, existing, today);
                dropExpiredPartitions(connection, catalog, existing, today);
            } catch (Exception e) {
                log.error("审计归档失败 物理库={} code={} 原因={}",
                        index, ErrorCode.AUDIT_EXPORT_FAILED.getCode(), e.getMessage(), e);
            }
        }
    }

    /** 读取当前库 {@code audit_log} 已存在的分区名。 */
    private Set<String> listPartitions(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        String sql = "SELECT PARTITION_NAME FROM information_schema.PARTITIONS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    /** 补齐「当前月 ~ 当前月 + pre-create-months」中缺失的月度分区。 */
    private void addFuturePartitions(Connection connection, String catalog, Set<String> existing, LocalDate today)
            throws SQLException {
        YearMonth current = YearMonth.from(today);
        List<YearMonth> missing = new ArrayList<>();
        for (int offset = 0; offset <= archive.getPreCreateMonths(); offset++) {
            YearMonth month = current.plusMonths(offset);
            if (!existing.contains(PARTITION_PREFIX + month.format(SUFFIX_FORMAT))) {
                missing.add(month);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        missing.sort(Comparator.naturalOrder());
        StringBuilder body = new StringBuilder();
        for (YearMonth month : missing) {
            String upperBound = month.plusMonths(1).atDay(1).format(DAY_FORMAT);
            body.append(", PARTITION ").append(PARTITION_PREFIX).append(month.format(SUFFIX_FORMAT))
                    .append(" VALUES LESS THAN (TO_DAYS('").append(upperBound).append("'))");
        }
        body.append(", PARTITION ").append(PMAX).append(" VALUES LESS THAN MAXVALUE");
        execute(connection, "ALTER TABLE " + TABLE + " REORGANIZE PARTITION " + PMAX
                + " INTO (" + body.substring(2) + ")");
        log.info("审计归档：物理库 {} 新增分区 {}", catalog, missing);
    }

    /** 冷备并删除上界早于保留起点的超期月度分区。 */
    private void dropExpiredPartitions(Connection connection, String catalog, Set<String> existing, LocalDate today)
            throws SQLException, IOException {
        LocalDate retentionStart = today.withDayOfMonth(1).minusMonths(archive.getRetentionMonths());
        List<String> expired = new ArrayList<>();
        for (String name : existing) {
            YearMonth month = parsePartitionMonth(name);
            if (month != null && !month.plusMonths(1).atDay(1).isAfter(retentionStart)) {
                expired.add(name);
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        expired.sort(Comparator.naturalOrder());
        for (String name : expired) {
            exportPartition(connection, catalog, name);
            execute(connection, "ALTER TABLE " + TABLE + " DROP PARTITION " + name);
            log.info("审计归档：物理库 {} 已删除超期分区 {}", catalog, name);
        }
    }

    /** 将单个分区全量导出为 gzip CSV 冷备文件。 */
    private void exportPartition(Connection connection, String catalog, String partition)
            throws SQLException, IOException {
        Path dir = Paths.get(archive.getBackupDir(), catalog);
        Files.createDirectories(dir);
        Path file = dir.resolve(TABLE + "-" + partition + ".csv.gz");
        long rows = 0L;
        String sql = "SELECT * FROM " + TABLE + " PARTITION (" + partition + ")";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql);
             Writer writer = new BufferedWriter(new OutputStreamWriter(
                     new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8))) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                if (i > 1) {
                    writer.write(',');
                }
                writer.write(escape(meta.getColumnLabel(i)));
            }
            writer.write('\n');
            while (rs.next()) {
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        writer.write(',');
                    }
                    writer.write(escape(rs.getString(i)));
                }
                writer.write('\n');
                rows++;
            }
        }
        log.info("审计归档：物理库 {} 分区 {} 已冷备 {} 行 -> {}", catalog, partition, rows, file.toAbsolutePath());
    }

    /** 解析月度分区名（{@code pYYYYMM}），非月度分区（如 pmax）返回 {@code null}。 */
    private static YearMonth parsePartitionMonth(String name) {
        if (name == null || name.length() != 7 || !name.startsWith(PARTITION_PREFIX)) {
            return null;
        }
        try {
            return YearMonth.parse(name.substring(1), SUFFIX_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** CSV 字段转义（含逗号、引号或换行时加引号，内部引号双写）。 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
