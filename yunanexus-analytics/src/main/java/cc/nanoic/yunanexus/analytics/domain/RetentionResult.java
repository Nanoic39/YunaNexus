package cc.nanoic.yunanexus.analytics.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 留存分析结果（{@code GET /api/v1/analytics/retention}）。
 *
 * @author yunanexus
 */
@Data
public class RetentionResult {

    /** 留存周期表头（如 D0 / D1 / D7 / D30） */
    private List<String> periods = new ArrayList<>();

    /** 各批次（cohort）留存行 */
    private List<RetentionRow> rows = new ArrayList<>();

    public static RetentionResult empty() {
        return new RetentionResult();
    }

    /** 单个批次留存数据 */
    @Data
    public static class RetentionRow {
        /** 批次（如注册日期 2026-10-14） */
        private String cohort;
        /** 与 {@link #periods} 对齐的留存率（0~1），缺失为 null */
        private List<Double> rates = new ArrayList<>();

        public RetentionRow() {
        }

        public RetentionRow(String cohort) {
            this.cohort = cohort;
        }
    }
}
