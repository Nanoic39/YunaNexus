package cc.nanoic.yunanexus.common.web.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 委托认证中心验证会话与实时管理员角色；不接受浏览器提供的角色或用户 ID。 */
@Component
public class AdminBearerVerifier {
    private final String verificationUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public AdminBearerVerifier(@Value("${yunanexus.admin.verification-url:}") String verificationUrl) {
        this.verificationUrl = verificationUrl;
    }

    /** 返回 200/401/403/503；任何配置、网络或响应异常均拒绝。 */
    public int verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) return 401;
        if (verificationUrl == null || verificationUrl.isBlank()) return 503;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(verificationUrl))
                .timeout(Duration.ofSeconds(3)).header("Authorization", authorization).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() == 401 || response.statusCode() == 403) return response.statusCode();
                if (response.statusCode() != 200) return 503;
                byte[] bytes = body.readNBytes(4097);
                if (bytes.length > 4096) return 503;
                var result = mapper.readTree(bytes);
                if (result.path("code").asInt() != 200 || !result.path("data").isBoolean()) return 503;
                return result.path("data").asBoolean() ? 200 : 403;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 503;
        } catch (Exception e) {
            return 503;
        }
    }
}
