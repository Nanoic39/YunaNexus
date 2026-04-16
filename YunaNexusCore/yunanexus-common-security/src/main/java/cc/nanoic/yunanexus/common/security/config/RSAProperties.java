package cc.nanoic.yunanexus.common.security.config;

import cc.nanoic.yunanexus.common.security.constant.RSAConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yunanexus.security.rsa")
public class RSAProperties {
    private String secretDir = RSAConstant.SECRET_DIR;
    private String publicKeyFile = RSAConstant.PUBLIC_KEY_FILENAME;
    private String privateKeyFile = RSAConstant.PRIVATE_KEY_FILENAME;
    private int keySize = RSAConstant.KEY_SIZE;
    private boolean autoGenerate = true;
}
