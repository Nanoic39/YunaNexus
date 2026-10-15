package cc.nanoic.yunanexus.analytics.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 漏斗分析结果（{@code GET /api/v1/analytics/funnel}）。
 *
 * @author yunanexus
 */
@Data
public class FunnelResult {

    /** 漏斗各步骤（按输入顺序），每步携带到达用户数 */
    private List<FunnelStep> steps = new ArrayList<>();

    public static FunnelResult empty() {
        return new FunnelResult();
    }

    /** 单个漏斗步骤 */
    @Data
    public static class FunnelStep {
        /** 步骤事件类型 */
        private String step;
        /** 到达该步骤的用户数 */
        private long users;

        public FunnelStep() {
        }

        public FunnelStep(String step, long users) {
            this.step = step;
            this.users = users;
        }
    }
}
