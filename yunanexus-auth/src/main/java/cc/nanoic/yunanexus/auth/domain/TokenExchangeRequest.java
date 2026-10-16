package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TokenExchangeRequest {

    @NotBlank(message = "code 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9-_]{43}$", message = "code 格式无效")
    private String code;
    @Size(max=128)
    private String clientSecret;

    @NotBlank(message = "codeVerifier 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9-._~]{43,128}$", message = "codeVerifier 格式无效")
    private String codeVerifier;

    @NotBlank(message = "clientCode 不能为空")
    @Size(min = 1, max = 64, message = "clientCode 长度必须在 1..64 之间")
    private String clientCode;

    @NotBlank(message = "redirectUri 不能为空")
    @Size(max = 500, message = "redirectUri 长度不能超过 500")
    private String redirectUri;
}
