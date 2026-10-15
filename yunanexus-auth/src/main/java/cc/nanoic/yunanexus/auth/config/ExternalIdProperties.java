package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部 ID 密钥配置（版本化，ADR-002 / ADR-014）。<p>
 *
 * 前缀 {@code yunanexus.auth.external-id}，维护外部 ID 编解码所需的 AES / HMAC 密钥版本列表。
 * 注册时使用最新版本密钥编码外部 ID 并落库（{@code auth_user.external_id}，只读不重算）；
 * 解码按 payload 首字节版本号选择对应密钥，实现密钥平滑轮换。<p>
 *
 * <b>密钥来源</b>：{@code aes-key-base64} / {@code hmac-key-base64} 为 Base64 编码，
 * 生产环境从 K8s Secret / 环境变量注入，<b>禁止硬编码</b>（ADR-011）。配置示例：
 * <pre>
 * yunanexus:
 *   auth:
 *     external-id:
 *       keys:
 *         - version: 1
 *           aes-key-base64: ${EXTERNAL_ID_AES_KEY_V1}
 *           hmac-key-base64: ${EXTERNAL_ID_HMAC_KEY_V1}
 * </pre>
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.auth.external-id")
public class ExternalIdProperties {

    /** 密钥版本列表，按 {@code version} 唯一；编码使用列表末位（最新）版本 */
    private List<KeyVersion> keys = new ArrayList<>();

    /**
     * 单个密钥版本（version + AES 密钥 + HMAC 密钥）。
     */
    @Data
    public static class KeyVersion {

        /** 版本号（0~255），写入 payload 首字节 */
        private int version;

        /** Base64 编码的 AES 密钥（16/24/32 字节） */
        private String aesKeyBase64;

        /** Base64 编码的 HMAC 密钥 */
        private String hmacKeyBase64;
    }
}
