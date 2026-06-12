package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class LoginResponse {
    private String accessToken;
    private String uuid;
}
