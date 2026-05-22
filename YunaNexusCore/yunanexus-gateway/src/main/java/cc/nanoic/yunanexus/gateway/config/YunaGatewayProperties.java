package cc.nanoic.yunanexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "yunanexus.gateway")
public class YunaGatewayProperties {
    private Security security = new Security();
    private Cors cors = new Cors();
    private Sentinel sentinel = new Sentinel();

    @Data
    public static class Security {
        private boolean enabled = true;
        private String authParseUri = "lb://YunaNexus-AuthService/oauth/parse";
        @DurationUnit(ChronoUnit.MILLIS)
        private Duration authTimeout = Duration.ofMillis(3000);
        private DataSize maxBodySize = DataSize.ofMegabytes(20);
        private List<String> permitPaths = new ArrayList<>();
        private List<String> ipWhitelist = new ArrayList<>();
        private List<String> ipBlacklist = new ArrayList<>();
        private boolean forwardUserContext = true;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> exposedHeaders = new ArrayList<>(List.of("Authorization", "Content-Disposition"));
        private boolean allowCredentials;
        private long maxAge = 3600;
    }

    @Data
    public static class Sentinel {
        private List<ApiDefinitionProperty> apiDefinitions = new ArrayList<>();
        private List<FlowRuleProperty> flowRules = new ArrayList<>();
    }

    @Data
    public static class ApiDefinitionProperty {
        private String apiName;
        private List<String> patterns = new ArrayList<>();
    }

    @Data
    public static class FlowRuleProperty {
        private String resource;
        private double count = 100d;
        private int intervalSec = 1;
        private int burst = 0;
    }
}
