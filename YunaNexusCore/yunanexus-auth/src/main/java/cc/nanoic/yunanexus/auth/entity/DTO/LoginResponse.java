package cc.nanoic.yunanexus.auth.entity.DTO;

import cc.nanoic.yunanexus.auth.entity.VO.ResourceVO;
import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String uuid;
    private List<ResourceVO> menus;
    private List<String> buttons;
}
