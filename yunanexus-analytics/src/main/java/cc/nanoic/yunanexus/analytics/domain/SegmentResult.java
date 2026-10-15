package cc.nanoic.yunanexus.analytics.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户分群结果（{@code GET /api/v1/analytics/segments}）。
 *
 * @author yunanexus
 */
@Data
public class SegmentResult {

    /** 命中的用户外部 ID 列表 */
    private List<String> userIds = new ArrayList<>();

    /** 命中总数 */
    private long total;

    public static SegmentResult empty() {
        return new SegmentResult();
    }
}
