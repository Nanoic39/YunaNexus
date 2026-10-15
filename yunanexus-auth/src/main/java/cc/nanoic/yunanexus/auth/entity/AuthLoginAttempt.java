package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_login_attempt")
public class AuthLoginAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String loginType;
    private Boolean success;
    private String failReason;
    private String ip;
    private String userAgent;
    private String deviceId;
    private String country;
    private String province;
    private String city;
    private LocalDateTime createTime;
}
