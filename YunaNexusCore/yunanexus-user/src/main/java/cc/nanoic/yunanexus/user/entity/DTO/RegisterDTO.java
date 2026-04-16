package cc.nanoic.yunanexus.user.entity.DTO;

import lombok.Data;

@Data
public class RegisterDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码()
     */
    private String password;
}
