package cc.nanoic.yunanexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "yunanexus.gateway.shard")
public class ShardRouteProperties {

    private List<RouteConfig> routes = new ArrayList<>();

    @Data
    public static class RouteConfig {
        private String baseName;
        private List<String> paths = new ArrayList<>();
        private List<String> excludePaths = new ArrayList<>();

        public boolean matches(String requestPath) {
            for (String exclude : excludePaths) {
                if (requestPath.equals(exclude) || requestPath.startsWith(exclude + "/")
                        || requestPath.startsWith(exclude + "?")) {
                    return false;
                }
            }
            for (String prefix : paths) {
                if (requestPath.equals(prefix) || requestPath.startsWith(prefix + "/")
                        || requestPath.startsWith(prefix + "?")) {
                    return true;
                }
            }
            return false;
        }
    }
}