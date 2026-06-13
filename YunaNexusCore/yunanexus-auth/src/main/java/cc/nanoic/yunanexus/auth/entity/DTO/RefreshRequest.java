package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken;
}