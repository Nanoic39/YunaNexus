package cc.nanoic.yunanexus.auth.domain;

import lombok.Data;

/** 认证库注册对账快照；仅包含聚合数量，不携带用户身份或联系方式。 */
@Data
public class RegisterOutboxReconciliation {
    private long pending;
    private long failed;
    private long overdue;
    private long missing;
    private long orphan;
    private long mismatch;

    /** 判断是否存在需处理的失败、超时或本地数据差异。 */
    public boolean hasAnomalies() {
        return failed > 0 || overdue > 0 || missing > 0 || orphan > 0 || mismatch > 0;
    }
}
