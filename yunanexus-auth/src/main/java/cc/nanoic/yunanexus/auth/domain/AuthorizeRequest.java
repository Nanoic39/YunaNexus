package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthorizeRequest {

    @NotBlank(message = "state 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9-._~]{43,256}$", message = "state 格式无效")
    private String state;

    @NotBlank(message = "codeChallenge 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9-_]{43}$", message = "codeChallenge 必须为 S256 方法")
    private String codeChallenge;

    @NotBlank(message = "codeChallengeMethod 不能为空")
    @Pattern(regexp = "^S256$", message = "仅支持 S256")
    private String codeChallengeMethod = "S256";

    @NotBlank(message = "clientCode 不能为空")
    @Size(min = 1, max = 64, message = "clientCode 长度必须在 1..64 之间")
    private String clientCode;

    @NotBlank(message = "redirectUri 不能为空")
    @Size(max = 500, message = "redirectUri 长度不能超过 500")
    private String redirectUri;
}
