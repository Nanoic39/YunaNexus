package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username; // 用户名
    private String password; // 密码
    private String email; // 邮箱
    private String verifyCode; // 邮箱验证码
    private String nickname; // 昵称
    private String gender; // 性别
    private String adminInitKey; // 超级管理员认证密钥
}
