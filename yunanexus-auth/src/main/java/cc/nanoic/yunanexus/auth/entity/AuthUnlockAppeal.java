package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_unlock_appeal")
public class AuthUnlockAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String appealReason;
    private String contact;
    private Integer status;
    private String auditor;
    private String auditRemark;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
