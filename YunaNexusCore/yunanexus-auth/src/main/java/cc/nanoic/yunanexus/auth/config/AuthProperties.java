package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "yunanexus.auth")
public class AuthProperties {

    private List<BuiltinClients> builtinClients;
    private Jwt jwt;
    private Rsa rsa;
    private Aes aes;

    @Data
    public static class BuiltinClients {
        private String clientName;
        private Integer clientType;
        private String scope;
        private String grantTypes;
        private String redirectUri;
    }

    @Data
    public static class Jwt {
        private String secret;
        private Long accessExp;
        private Long refreshExp;
    }

    @Data
    public static class Rsa {
        private String keyPath = "./SecretKey";
    }

    @Data
    public static class Aes {
        private String keyPath = "./SecretKey";
    }
}
