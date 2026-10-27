package cc.nanoic.yunanexus.audit.chain;

import cc.nanoic.yunanexus.audit.entity.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防篡改链的摘要与校验逻辑。
 *
 * <p>这些用例守的是"审计模块自己可信"这条底线：摘要必须确定、字段拼接不能有歧义、
 * 篡改必须能被定位到具体那一条。
 */
class AuditChainHasherTest {

    private static AuditLog log(long id, String params) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setUserId(7L);
        log.setShardIndex(3);
        log.setUsername("alice");
        log.setService("user");
        log.setModule("account");
        log.setAction("UPDATE");
        log.setMethod("PUT");
        log.setUri("/api/v1/users/7");
        log.setParams(params);
        log.setResult("ok");
        log.setStatus(AuditLog.STATUS_SUCCESS);
        log.setIp("10.0.0.1");
        log.setDurationMs(12L);
        log.setTraceId("trace-1");
        log.setCreateTime(LocalDateTime.of(2026, 10, 27, 10, 0, 0, 123_000_000));
        return log;
    }

    @Test
    @DisplayName("同一记录多次计算结果一致")
    void digestIsDeterministic() {
        assertEquals(AuditChainHasher.digest(log(1, "{}")), AuditChainHasher.digest(log(1, "{}")));
    }

    @Test
    @DisplayName("任一参与字段变化都会改变摘要")
    void digestChangesWithAnyField() {
        AuditLog base = log(1, "{}");
        String expected = AuditChainHasher.digest(base);
        AuditLog changed = log(1, "{}");
        changed.setStatus(AuditLog.STATUS_FAILED);
        assertNotEquals(expected, AuditChainHasher.digest(changed));
    }

    @Test
    @DisplayName("字段拼接无歧义：解析后相同不等于原文相同")
    void canonicalizationHasNoAmbiguity() {
        // 不用分隔符时 "x,y"+"z" 与 "x"+"y,z" 会拼成同一串
        assertNotEquals(
                AuditChainHasher.digest(log(1, "x,y")),
                AuditChainHasher.digest(log(1, "x")));
        AuditLog left = log(1, "x,y");
        left.setResult("z");
        AuditLog right = log(1, "x");
        right.setResult("y,z");
        assertNotEquals(AuditChainHasher.digest(left), AuditChainHasher.digest(right));
    }

    @Test
    @DisplayName("null 与字面量 \"null\" 必须区分")
    void nullAndLiteralNullDiffer() {
        AuditLog withNull = log(1, null);
        AuditLog withText = log(1, "null");
        assertNotEquals(AuditChainHasher.digest(withNull), AuditChainHasher.digest(withText));
    }

    @Test
    @DisplayName("链摘要包含前驱，换前驱即变")
    void chainDigestDependsOnPrev() {
        AuditLog entry = log(1, "{}");
        assertNotEquals(
                AuditChainHasher.chainDigest(AuditChainHasher.GENESIS, entry),
                AuditChainHasher.chainDigest("a".repeat(64), entry));
    }

    @Test
    @DisplayName("前驱为空按链首处理")
    void emptyPrevFallsBackToGenesis() {
        AuditLog entry = log(1, "{}");
        assertEquals(
                AuditChainHasher.chainDigest(AuditChainHasher.GENESIS, entry),
                AuditChainHasher.chainDigest(null, entry));
    }

    @Test
    @DisplayName("verifies 只认严格匹配")
    void verifiesRequiresExactDigest() {
        AuditLog entry = log(1, "{}");
        entry.setRecordHash(AuditChainHasher.chainDigest(AuditChainHasher.GENESIS, entry));
        assertTrue(AuditChainHasher.verifies(entry, AuditChainHasher.GENESIS));
        assertFalse(AuditChainHasher.verifies(entry, "b".repeat(64)));
    }

    @Test
    @DisplayName("链首缺失摘要时 verifies 为假而不是抛错")
    void verifiesHandlesMissingHash() {
        assertFalse(AuditChainHasher.verifies(log(1, "{}"), AuditChainHasher.GENESIS));
        assertFalse(AuditChainHasher.verifies(null, AuditChainHasher.GENESIS));
    }

    /** 构造一段互链的合法记录 */
    private static List<AuditLog> chain(int size) {
        List<AuditLog> logs = new ArrayList<>();
        String previous = AuditChainHasher.GENESIS;
        for (int index = 1; index <= size; index++) {
            AuditLog entry = log(index, "{\"index\":" + index + "}");
            entry.setPrevHash(previous);
            entry.setRecordHash(AuditChainHasher.chainDigest(previous, entry));
            previous = entry.getRecordHash();
            logs.add(entry);
        }
        return logs;
    }

    @Test
    @DisplayName("完整链校验通过")
    void verifiesIntactChain() {
        AuditChainVerifier.Result result = AuditChainVerifier.verify(chain(5));
        assertTrue(result.intact());
        assertEquals(5, result.total());
        assertNull(result.brokenAt());
    }

    @Test
    @DisplayName("中间某条被改写，能定位到具体那一条")
    void detectsTamperedRecord() {
        List<AuditLog> logs = chain(5);
        logs.get(2).setParams("{\"forged\":true}");
        AuditChainVerifier.Result result = AuditChainVerifier.verify(logs);
        assertFalse(result.intact());
        assertEquals(3L, result.brokenAt());
        assertEquals("record_hash_mismatch", result.reason());
    }

    @Test
    @DisplayName("记录被删除后，后继的 prev_hash 对不上")
    void detectsDeletedRecord() {
        List<AuditLog> logs = chain(5);
        logs.remove(1);
        AuditChainVerifier.Result result = AuditChainVerifier.verify(logs);
        assertFalse(result.intact());
        // 断点报在"后继那条"（id=3）：它的 prev_hash 指向已消失的 id=2
        assertEquals(3L, result.brokenAt());
        assertEquals("prev_hash_mismatch", result.reason());
    }

    @Test
    @DisplayName("整条摘要被清空时判为缺失")
    void detectsMissingHash() {
        List<AuditLog> logs = chain(3);
        logs.get(1).setRecordHash("");
        AuditChainVerifier.Result result = AuditChainVerifier.verify(logs);
        assertFalse(result.intact());
        assertEquals("missing_record_hash", result.reason());
    }

    @Test
    @DisplayName("空区间视为完整")
    void emptyRangeIsIntact() {
        assertTrue(AuditChainVerifier.verify(List.of()).intact());
        assertEquals(0, AuditChainVerifier.verify((List<AuditLog>) null).total());
    }

    @Test
    @DisplayName("可指定起始前驱，支持分段校验")
    void supportsSegmentVerification() {
        List<AuditLog> logs = chain(5);
        List<AuditLog> tail = logs.subList(3, 5);
        String handover = logs.get(2).getRecordHash();
        assertTrue(AuditChainVerifier.verify(tail, handover).intact());
        assertFalse(AuditChainVerifier.verify(tail, AuditChainHasher.GENESIS).intact());
    }

    @Test
    @DisplayName("断裂结论含位置与原因，便于告警直出")
    void describeMentionsLocation() {
        List<AuditLog> logs = chain(4);
        logs.get(3).setAction("DELETE");
        String text = AuditChainVerifier.describe(AuditChainVerifier.verify(logs));
        assertTrue(text.contains("4"), text);
        assertTrue(text.contains("record_hash_mismatch"), text);
    }
}
