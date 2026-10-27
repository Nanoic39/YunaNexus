package cc.nanoic.yunanexus.audit.chain;

import cc.nanoic.yunanexus.audit.entity.AuditLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 链完整性校验：把"一串记录"判定成"完整 / 在第几条断裂"。<p>
 *
 * 之所以单独成类而不塞进 service：校验是纯计算，能在没有数据库、
 * 没有 Spring 上下文的环境下跑，因此可以用单测把断裂定位逻辑钉死——
 * 审计模块恰恰是最需要"自己可信"的模块。
 *
 * @author yunanexus
 */
public final class AuditChainVerifier {

    private AuditChainVerifier() {
    }

    /**
     * 校验结果。
     *
     * @param total    参与校验的记录数
     * @param intact   是否整段完整
     * @param brokenAt 首个断裂记录的 id；完整时为 {@code null}
     * @param reason   断裂原因；完整时为 {@code null}
     */
    public record Result(int total, boolean intact, Long brokenAt, String reason) {
    }

    /**
     * 按 {@code id} 升序校验一段记录。
     *
     * @param logs             同一分片的连续记录（须按 id 升序，首条的 prevHash 应为 {@link AuditChainHasher#GENESIS}
     *                         除非调用方另行传入起始前驱）
     * @param expectedPrevHash 首条记录期望的前驱摘要；传 {@code null} 表示从链首开始校验
     * @return 校验结果
     */
    public static Result verify(List<AuditLog> logs, String expectedPrevHash) {
        if (logs == null || logs.isEmpty()) {
            return new Result(0, true, null, null);
        }
        String previous = (expectedPrevHash == null || expectedPrevHash.isEmpty())
                ? AuditChainHasher.GENESIS : expectedPrevHash;
        int verified = 0;
        for (AuditLog log : logs) {
            verified++;
            if (log.getRecordHash() == null || log.getRecordHash().isEmpty()) {
                return new Result(verified, false, log.getId(), "missing_record_hash");
            }
            if (log.getPrevHash() != null && !log.getPrevHash().isEmpty()
                    && !log.getPrevHash().equalsIgnoreCase(previous)) {
                return new Result(verified, false, log.getId(), "prev_hash_mismatch");
            }
            if (!AuditChainHasher.verifies(log, previous)) {
                return new Result(verified, false, log.getId(), "record_hash_mismatch");
            }
            previous = log.getRecordHash();
        }
        return new Result(verified, true, null, null);
    }

    /**
     * 便捷方法：直接给一整串记录校验。
     *
     * @param logs 按 id 升序的记录
     * @return 校验结果
     */
    public static Result verify(List<AuditLog> logs) {
        return verify(logs, null);
    }

    /**
     * 把校验失败的信息渲染成人类可读的结论，供接口与告警复用。
     *
     * @param result 校验结果
     * @return 描述文本
     */
    public static String describe(Result result) {
        if (result.intact()) {
            return "审计链完整：已校验 " + result.total() + " 条记录";
        }
        List<String> parts = new ArrayList<>(2);
        parts.add("审计链在第 " + result.brokenAt() + " 条断裂");
        parts.add("原因：" + result.reason());
        parts.add("已校验 " + result.total() + " 条");
        return String.join(" | ", parts);
    }
}
