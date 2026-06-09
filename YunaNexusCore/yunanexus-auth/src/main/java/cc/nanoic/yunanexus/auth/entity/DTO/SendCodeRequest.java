package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class SendCodeRequest {
    private String email; // 要发送验证码的有邮箱
}
