package cc.nanoic.yunanexus.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT 签名密钥配置（kid 版本化，ADR-015）。<p>
 *
 * 前缀 {@code yunanexus.auth.jwt}，维护 RSA 签名密钥列表。每个密钥以 {@code kid} 标识，
 * 签发时使用 {@code activeKid} 指定的密钥，验证时按令牌头携带的 {@code kid} 选择对应公钥，
 * 实现密钥平滑轮换。历史 kid 仅需公钥，active kid 必须同时提供私钥。
 *
 * <b>密钥来源</b>：{@code publicKeyBase64} / {@code privateKeyBase64} 为 Base64 编码的
 * X.509 DER / PKCS#8 DER 密钥，生产环境从 K8s Secret / 环境变量注入，<b>禁止硬编码</b>（ADR-011）。
 * 配置示例：
 * <pre>
 * yunanexus:
 *   auth:
 *     jwt:
 *       active-kid: v2
 *       keys:
 *         - kid: v1
 *           public-key-base64: ${JWT_PUBLIC_KEY_V1}
 *         - kid: v2
 *           public-key-base64: ${JWT_PUBLIC_KEY_V2}
 *           private-key-base64: ${JWT_PRIVATE_KEY_V2}
 * </pre>
 *
 * @author yunanexus
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yunanexus.auth.jwt")
public class JwtProperties {

    /** 当前签名使用的 kid，必须在 {@link #keys} 中存在且配置了私钥 */
    private String activeKid;

    /** 签名密钥列表，按 {@code kid} 唯一；验证按 kid 选择公钥 */
    private List<SigningKey> keys = new ArrayList<>();

    /**
     * 单个签名密钥版本。
     */
    @Getter
    @Setter
    public static class SigningKey {

        /** 密钥 ID，写入 JWT 头 {@code kid} */
        private String kid;

        /** Base64 编码的 X.509 DER 公钥（RSA，≥ 2048 bit） */
        private String publicKeyBase64;

        /** Base64 编码的 PKCS#8 DER 私钥（RSA，≥ 2048 bit）；仅 active kid 需要 */
        private String privateKeyBase64;
    }
}
