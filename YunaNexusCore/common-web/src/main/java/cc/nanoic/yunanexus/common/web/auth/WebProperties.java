package cc.nanoic.yunanexus.common.web.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "yunanexus.web")
public class WebProperties {
    private Jwt jwt = new Jwt();
    private List<String> excludePaths = new ArrayList<>();

    public WebProperties() {
        excludePaths.add("/register/**");
        excludePaths.add("/login/**");
        excludePaths.add("/key/**");
        excludePaths.add("/oauth/token/**");
        excludePaths.add("/internal/**");
        excludePaths.add("/actuator/**");
        excludePaths.add("/error");
    }

    @Data
    public static class Jwt {
        private String secret;
    }
}
