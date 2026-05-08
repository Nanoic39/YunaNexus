package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("redis_prefix")
public class AuthRedisKeyPrefix {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String serviceName;
    private String keyCode;
    private String keyTemplate;
    private Long version;
    private Integer status;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}