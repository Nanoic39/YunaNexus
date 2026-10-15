package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分片决策配置。<p>
 *
 * 前缀 {@code yunanexus.auth.shard}。用户中心（阶段 5）落地前，注册时由认证中心按
 * 可插拔策略决策逻辑分片编号；策略与分片总数可配置，便于开发 / 测试 / 生产差异化。
 * 阶段 5 落地后，若改由用户中心决策，只需替换 {@code ShardAllocator} 实现。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.auth.shard")
public class ShardProperties {

    /** 逻辑分片总数（1~16，与外部 ID 4 bit 分片位对齐） */
    private int shardCount = 16;
}
