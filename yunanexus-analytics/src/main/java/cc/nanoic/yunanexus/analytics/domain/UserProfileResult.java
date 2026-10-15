package cc.nanoic.yunanexus.analytics.domain;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单用户画像结果（{@code GET /api/v1/analytics/profile}）。
 *
 * @author yunanexus
 */
@Data
public class UserProfileResult {

    /** 用户外部 ID */
    private String userId;

    /** 行为 / 兴趣标签 */
    private java.util.List<String> tags = new java.util.ArrayList<>();

    /** 事件类型 → 次数（近期统计） */
    private Map<String, Long> eventCounts = new LinkedHashMap<>();

    /** 价值分层（RFM，离线产出） */
    private String valueTier;

    public static UserProfileResult of(String userId) {
        UserProfileResult result = new UserProfileResult();
        result.userId = userId;
        return result;
    }
}
