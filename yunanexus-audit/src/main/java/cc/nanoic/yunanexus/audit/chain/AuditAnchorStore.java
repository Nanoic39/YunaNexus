package cc.nanoic.yunanexus.audit.chain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 锚定文件的读写（append-only JSONL）。<p>
 *
 * 刻意只用文件而不是新表：锚定要防的正是"有库权限的人改库"，写进同一个库等于没锚。
 * 文件层面对齐 WORM 语义靠的是外部机制（只读挂载 / 对象存储锁），
 * 本类只保证"只追加、不覆盖、不删除"。
 *
 * @author yunanexus
 */
public class AuditAnchorStore {

    private static final String FILE_NAME = "anchors.jsonl";

    private final Path directory;

    public AuditAnchorStore(String directory) {
        this.directory = Path.of(directory);
    }

    /**
     * 追加一批锚定记录。
     *
     * @param records 待写入记录
     * @return 写入条数
     * @throws IOException 目录不可写时抛出（由调用方决定是否告警）
     */
    public int append(List<AuditAnchorRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        Files.createDirectories(directory);
        StringBuilder text = new StringBuilder(records.size() * 120);
        for (AuditAnchorRecord record : records) {
            text.append(record.toLine()).append('\n');
        }
        Files.writeString(directory.resolve(FILE_NAME), text.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        return records.size();
    }

    /**
     * 读取每个分片最近一次锚定。
     *
     * @return 分片编号 → 最新锚定记录
     * @throws IOException 读取失败
     */
    public Map<Integer, AuditAnchorRecord> latestPerShard() throws IOException {
        Map<Integer, AuditAnchorRecord> latest = new HashMap<>();
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return latest;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            AuditAnchorRecord record = AuditAnchorRecord.parse(line);
            if (record == null) {
                continue;
            }
            // 文件是追加写，越靠后越新
            latest.put(record.shardIndex(), record);
        }
        return latest;
    }

    /**
     * 读取全部锚定记录（排查用）。
     *
     * @return 记录列表（按写入顺序）
     * @throws IOException 读取失败
     */
    public List<AuditAnchorRecord> all() throws IOException {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<AuditAnchorRecord> records = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            AuditAnchorRecord record = AuditAnchorRecord.parse(line);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }
}
