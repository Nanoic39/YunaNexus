package cc.nanoic.yunanexus.audit.chain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部锚定的读写语义。
 *
 * <p>锚定的价值全在"只追加、可追溯"，所以这里重点守：脏行不能让校验整体失败、
 * 每分片只认最新一条、重名文件不会被覆盖。
 */
class AuditAnchorStoreTest {

    private static AuditAnchorRecord record(int shard, long id, String hash) {
        return new AuditAnchorRecord(shard, id, hash, LocalDateTime.of(2026, 10, 27, 21, 5, 0));
    }

    @Test
    @DisplayName("写入后可读回，行格式自洽")
    void roundTrip(@TempDir Path dir) throws IOException {
        AuditAnchorStore store = new AuditAnchorStore(dir.toString());
        String hash = "a".repeat(64);
        assertEquals(1, store.append(List.of(record(3, 99L, hash))));

        AuditAnchorRecord parsed = store.latestPerShard().get(3);
        assertEquals(99L, parsed.id());
        assertEquals(hash, parsed.recordHash());
        assertEquals(LocalDateTime.of(2026, 10, 27, 21, 5, 0), parsed.anchoredAt());
    }

    @Test
    @DisplayName("同一分片多次锚定只取最新一条")
    void latestPerShardWins(@TempDir Path dir) throws IOException {
        AuditAnchorStore store = new AuditAnchorStore(dir.toString());
        store.append(List.of(record(1, 10L, "a".repeat(64))));
        store.append(List.of(record(1, 20L, "b".repeat(64))));
        Map<Integer, AuditAnchorRecord> latest = store.latestPerShard();
        assertEquals(20L, latest.get(1).id());
        assertEquals(2, store.all().size(), "历史锚定必须保留，否则事后无法回溯");
    }

    @Test
    @DisplayName("追加写不会覆盖历史锚定")
    void appendIsAddOnly(@TempDir Path dir) throws IOException {
        AuditAnchorStore store = new AuditAnchorStore(dir.toString());
        store.append(List.of(record(0, 1L, "c".repeat(64))));
        store.append(List.of(record(0, 2L, "d".repeat(64))));
        String content = Files.readString(dir.resolve("anchors.jsonl"));
        assertEquals(2, content.strip().split("\n").length);
    }

    @Test
    @DisplayName("脏行被跳过，不影响其余记录")
    void skipsCorruptedLines(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("anchors.jsonl"),
                "not json\n" + record(2, 7L, "e".repeat(64)).toLine() + "\n{\"shard\":}\n");
        AuditAnchorStore store = new AuditAnchorStore(dir.toString());
        Map<Integer, AuditAnchorRecord> latest = store.latestPerShard();
        assertEquals(1, latest.size());
        assertEquals(7L, latest.get(2).id());
    }

    @Test
    @DisplayName("文件不存在时返回空而不是抛错")
    void missingFileIsEmpty(@TempDir Path dir) throws IOException {
        assertTrue(new AuditAnchorStore(dir.toString()).latestPerShard().isEmpty());
    }

    @Test
    @DisplayName("非法行解析返回 null")
    void parseRejectsGarbage() {
        assertNull(AuditAnchorRecord.parse(null));
        assertNull(AuditAnchorRecord.parse(""));
        assertNull(AuditAnchorRecord.parse("{\"shard\":1}"));
        assertNull(AuditAnchorRecord.parse("plain text"));
    }
}
