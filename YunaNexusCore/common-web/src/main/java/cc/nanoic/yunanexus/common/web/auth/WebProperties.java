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
    /**
     * 前端站点 URL，用于 OAuth 授权等场景跳转登录页。
     * 默认 http://localhost:3000，生产环境通过 yunanexus.web.base-url 配置
     */
    private String baseUrl = "http://localhost:3000";

    public WebProperties() {
        excludePaths.add("/register/**");
        excludePaths.add("/login/**");
        excludePaths.add("/key/**");
        excludePaths.add("/oauth/token/**");
        excludePaths.add("/oauth/authorize/**");
        excludePaths.add("/internal/**");
        excludePaths.add("/actuator/**");
        excludePaths.add("/error");
    }

    @Data
    public static class Jwt {
        private String secret;
    }
}
