package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料实体（{@code sys_user}，每个物理分库各一张）。<p>
 *
 * {@code user_id} 为全局用户 ID（号段服务分配，不含分库信息），也是分库路由与
 * 幂等键；{@code external_id} 注册时由认证中心编码一次落库，此后只读。
 *
 * @author yunanexus
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局用户 ID */
    private Long userId;

    /** 逻辑分片编号（0~15） */
    private Integer shardIndex;

    /** 外部 ID */
    private String externalId;

    /** 账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号国家号 */
    private String mobileCountry;

    /** 手机号 */
    private String mobile;

    /** 状态：1-正常 0-禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
