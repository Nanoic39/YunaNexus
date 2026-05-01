package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oauth_clients")
public class OAuthClients {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户端配置UUID（系统主标识）
     */
    private String uuid;

    /**
     * 客户端类型(1：官方客户端，2：第三方应用)
     */
    private Integer clientType;

    /**
     * 审核状态(0：待审核，1：已通过，2：已拒绝)<br/>
     * 仅第三方应用需要审核，官方默认为已通过
     */
    private Integer auditStatus;

    /**
     * 审核意见<br/>
     * 仅第三方应用需要填写，官方默认为"官方应用【{该OAuth客户端配置创建人UUID}】"
     */
    private String auditOpinion;

    /**
     * 重定向URI白名单（逗号分隔）
     */
    private String redirectWhitelist;

    /**
     * 客户端密钥（加密后）
     */
    private String clientSecret;

    /**
     * 客户端名称（外显/输入校验）
     */
    private String clientName;

    /**
     * 资源ID列表
     */
    private String resourceIds;

    /**
     * 权限范围限制
     */
    private String scopeLimit;

    /**
     * 授权模式列表
     */
    private String authorizedGrantTypes;

    /**
     * 权限范围
     */
    private String scope;

    /**
     * 当前生效回调地址
     */
    private String redirectUri;

    /**
     * Access Token有效期（秒）
     */
    private Integer accessTokenValidity;

    /**
     * Refresh Token有效期（秒）
     */
    private Integer refreshTokenValidity;

    /**
     * 是否自动授权(0：否，1：是)
     */
    private Integer autoApprove;

    /**
     * 默认角色ID
     */
    private Long defaultRoleId;

    /**
     * 状态(0：禁用，1：启用)
     */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}