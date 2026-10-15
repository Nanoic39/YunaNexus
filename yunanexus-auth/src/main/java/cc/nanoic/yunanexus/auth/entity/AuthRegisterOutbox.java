package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 注册本地消息表（outbox，ADR-021）。<p>
 *
 * 与 {@code auth_user} 在同一本地事务内写入：注册成功即产生一条待同步记录。
 * 后台任务扫描 {@code status=0} 且 {@code next_retry_time <= now} 的记录，调用用户中心
 * 创建用户资料（阶段 5），成功置 {@code status=1}；失败按指数退避重试，超上限置
 * {@code status=2} 并告警。{@code user_id} 唯一键保证幂等（重复投递不产生重复记录）。
 *
 * @author yunanexus
 */
@Data
@TableName("auth_register_outbox")
public class AuthRegisterOutbox {

    /** 同步状态：待同步 */
    public static final int STATUS_PENDING = 0;
    /** 同步状态：已同步 */
    public static final int STATUS_DONE = 1;
    /** 同步状态：同步失败（超重试上限） */
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局用户 ID（唯一，与 auth_user.user_id 一致） */
    private Long userId;

    /** 外部 ID */
    private String externalId;

    /** 逻辑分片编号 */
    private Integer shardIndex;

    /** 账号 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 手机号国家号 */
    private String mobileCountry;

    /** 手机号 */
    private String mobile;

    /** 同步状态：0-待同步 1-已同步 2-同步失败 */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryTime;

    /** 最后一次失败原因（脱敏，不含敏感信息） */
    private String lastError;

    /** 同步成功时间 */
    private LocalDateTime syncedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
