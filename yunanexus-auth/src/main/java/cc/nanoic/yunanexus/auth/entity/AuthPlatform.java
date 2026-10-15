package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_platform")
public class AuthPlatform {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String clientCode;
    private Integer status;
    private LocalDateTime authorizedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
