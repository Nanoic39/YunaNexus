package cc.nanoic.yunanexus.audit.chain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 一条外部锚定记录：某个分片在某个时刻的链头。<p>
 *
 * 为什么要它：库内哈希链只能证明"记录之间没被动过"，但攻击者若能整库重算，
 * 链依然是自洽的。把链头<b>定期写进审计库之外的地方</b>（只读文件 / 对象存储 WORM），
 * 事后比对"当时的链头"和"现在的库"，才能发现整库被重写。
 *
 * @param shardIndex 逻辑分片编号
 * @param id         锚定时的链头记录 id
 * @param recordHash 锚定时的链头摘要
 * @param anchoredAt 锚定时间（ISO-8601）
 * @author yunanexus
 */
public record AuditAnchorRecord(int shardIndex, long id, String recordHash, LocalDateTime anchoredAt) {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 一行一个 JSON 对象，便于 append-only 追加与流式读取 */
    public String toLine() {
        return "{\"shard\":" + shardIndex
                + ",\"id\":" + id
                + ",\"hash\":\"" + recordHash + "\""
                + ",\"at\":\"" + TIME_FORMAT.format(anchoredAt) + "\"}";
    }

    /**
     * 解析一行锚定记录。
     *
     * @param line 单行文本
     * @return 锚定记录；格式非法时返回 {@code null}（脏行跳过而不是让整个校验失败）
     */
    public static AuditAnchorRecord parse(String line) {
        if (line == null || !line.startsWith("{") || !line.endsWith("}")) {
            return null;
        }
        try {
            Integer shard = intField(line, "\"shard\":");
            Long id = longField(line, "\"id\":");
            String hash = stringField(line, "\"hash\":\"");
            String at = stringField(line, "\"at\":\"");
            if (shard == null || id == null || hash == null || at == null) {
                return null;
            }
            return new AuditAnchorRecord(shard, id, hash, LocalDateTime.parse(at, TIME_FORMAT));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Integer intField(String line, String marker) {
        Long value = longField(line, marker);
        return value == null ? null : value.intValue();
    }

    private static Long longField(String line, String marker) {
        int start = line.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int from = start + marker.length();
        int end = from;
        while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '-')) {
            end++;
        }
        return end == from ? null : Long.parseLong(line.substring(from, end));
    }

    private static String stringField(String line, String marker) {
        int start = line.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int from = start + marker.length();
        int end = line.indexOf('"', from);
        return end < 0 ? null : line.substring(from, end);
    }
}
