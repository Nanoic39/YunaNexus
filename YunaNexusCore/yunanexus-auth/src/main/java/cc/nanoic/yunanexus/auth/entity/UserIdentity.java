package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_identity")
public class UserIdentity {
    private byte[] globalId; // 全局用户id
    private String username; // 用户名（登录用）
    private String password; // 密码
    private String email; // 邮箱
    private String phone; // 电话号
    private Integer status; // 帐号状态(0：注销，1：正常，2：封禁，3：冻结)
    private LocalDateTime createdAt; // 创建时间
}
