package cc.nanoic.yunanexus.meta.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 号段服务配置。<p>
 *
 * 前缀 {@code yunanexus.segment}，用于控制动态步长、预加载阈值、降级阈值等
 * 号段分配行为。所有阈值均可通过配置文件（本地 yaml / 后续 Nacos）调整，
 * 便于开发 / 测试 / 生产环境差异化配置。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.segment")
public class IdSegmentProperties {

    /** 最小步长，动态步长下限 */
    private int minStep = 1000;

    /** 最大步长，动态步长上限 */
    private int maxStep = 10000;

    /** 初始/默认步长，首次加载及无历史速率时使用 */
    private int defaultStep = 2000;

    /** 使用率阈值，超过即触发异步预加载下一号段（0~1） */
    private double preloadThreshold = 0.8;

    /** 期望的号段缓冲时长（秒），动态步长据此反推目标步长 */
    private long targetBufferSeconds = 900;

    /** 步长平滑系数（0~1），越大越贴近目标步长，越小越平滑（防震荡） */
    private double stepSmoothFactor = 0.5;

    /** 乐观锁 CAS 领取号段的重试次数 */
    private int loadRetryTimes = 3;

    /** 异步预加载线程池大小 */
    private int asyncLoaderThreads = 4;

    /** 连续异步预加载失败次数阈值，超过则进入降级状态 */
    private int maxConsecutiveAsyncFailures = 5;
}