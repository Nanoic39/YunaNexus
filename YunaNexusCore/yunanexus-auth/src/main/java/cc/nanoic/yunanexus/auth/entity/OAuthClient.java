package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oauth_client")
public class OAuthClient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid; // 客户端UUID
    private String clientName; // 客户端名称
    private String clientSecret; // 客户端密钥(BCrypt哈希)
    @TableField(exist = false)
    private String clientSecretEncrypted; // 客户端密钥(AES加密原文，允许用户反复查看)
    private Integer clientType; // 客户端类型 1:官方 2:第三方
    private String grantTypes; // 认证方式 账号密码：password,Token刷新：refresh_token,OAuth：authorization_code
    private String scope; // 授权范围
    private String redirectUri; // 回调地址
    private String description; // 申请说明
    private Integer auditStatus; // 审核状态：0:待审核 1:已通过 2:已拒绝(官方直接=1)
    private byte[] applicantGlobalId; // 申请人global_id(第三方必填)
    private byte[] auditorGlobalId; // 审核人id(第三方审核后必填)
    private String auditOpinion; // 审核意见
    private LocalDateTime auditedAt; // 审核时间
    private Integer status; // 启用状态 0:禁用 1:启用
    private LocalDateTime createdAt; // 记录创建时间
    private LocalDateTime updatedAt; // 记录更新时间
}
