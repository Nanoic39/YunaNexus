package cc.nanoic.yunanexus.analytics.controller;

import cc.nanoic.yunanexus.analytics.domain.FunnelResult;
import cc.nanoic.yunanexus.analytics.domain.RetentionResult;
import cc.nanoic.yunanexus.analytics.domain.SegmentResult;
import cc.nanoic.yunanexus.analytics.domain.UserProfileResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分析查询接口（roadmap 10.4，api-contract 8.2）。<p>
 *
 * 提供漏斗 / 留存 / 分群 / 画像查询。查询走 ClickHouse 离线 / 准实时结果，
 * 阶段 10.4 落地前先返回空结果占位（{@code empty()}），契约先行、不阻塞前端联调。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    /**
     * 漏斗分析：按 {@code steps} 顺序统计各步骤到达用户数。
     *
     * @param steps 步骤事件类型，逗号分隔（如 {@code $view,$cart,$order}）
     */
    @GetMapping("/funnel")
    public Result<FunnelResult> funnel(@RequestParam(value = "steps", required = false) String steps) {
        return Result.success(FunnelResult.empty());
    }

    /**
     * 留存分析：按 {@code cohort} 维度返回批次留存率。
     *
     * @param cohort 留存批次维度（如注册日期）
     */
    @GetMapping("/retention")
    public Result<RetentionResult> retention(@RequestParam(value = "cohort", required = false) String cohort) {
        return Result.success(RetentionResult.empty());
    }

    /**
     * 用户分群：按 {@code condition} 返回命中的用户外部 ID 列表。
     *
     * @param condition 分群条件表达式
     */
    @GetMapping("/segments")
    public Result<SegmentResult> segments(@RequestParam(value = "condition", required = false) String condition) {
        return Result.success(SegmentResult.empty());
    }

    /**
     * 单用户画像：返回指定用户的标签 / 事件计数 / 价值分层。
     *
     * @param userId 用户外部 ID
     */
    @GetMapping("/profile")
    public Result<UserProfileResult> profile(@RequestParam(value = "userId", required = false) String userId) {
        return Result.success(UserProfileResult.of(userId));
    }
}
