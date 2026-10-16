package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer shardIndex;
    private String externalId;
    private String username;
    private String email;
    private String mobileCountry;
    private String mobile;
    private String password;
    private Integer passwordResetRequired;
    private Integer status;
    private LocalDateTime lockedUntil;
    private String lockReason;
    private Integer failCount;
    private LocalDateTime lastFailTime;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime passwordUpdateTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
