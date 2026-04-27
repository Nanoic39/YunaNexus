package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("related_user_oauth_oauth_client") // What The Fuck Name?
public class RelatedUserOauthOauthClient {

    /**
     * 用户与OAuth客户端关联表主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户表外键id(关联users表的id字段)
     */
    private Long userId;

    /**
     * OAuth客户端配置UUID(关联Auth服务oauth_clients.uuid)
     */
    private String clientUuid;

    /**
     * 关联状态(0：禁用，1：启用)
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     */
    private LocalDateTime updateTime;

}
