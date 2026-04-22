package cc.nanoic.yunanexus.common.security.starter;

import cc.nanoic.yunanexus.common.security.aspect.RSADecryptRequestAspect;
import cc.nanoic.yunanexus.common.security.aspect.RSAEncryptResponseAspect;
import cc.nanoic.yunanexus.common.security.config.RSAProperties;
import cc.nanoic.yunanexus.common.security.util.RSAKeyUtil;
import cc.nanoic.yunanexus.common.security.util.RSAUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.security.KeyPair;

@AutoConfiguration
@EnableConfigurationProperties(RSAProperties.class)
public class YunaNexusSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyPair rsaKeyPair(RSAProperties properties) {
        return RSAKeyUtil.loadOrGenerate(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RSAUtil rsaUtil(KeyPair keyPair) {
        return new RSAUtil(keyPair);
    }

    @Bean
    @ConditionalOnProperty(prefix = "yunanexus.security.rsa", name = "enable-aspect", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RSADecryptRequestAspect rsaDecryptRequestAspect(RSAUtil rsaUtil) {
        return new RSADecryptRequestAspect(rsaUtil);
    }

    @Bean
    @ConditionalOnProperty(prefix = "yunanexus.security.rsa", name = "enable-aspect", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RSAEncryptResponseAspect rsaEncryptResponseAspect(RSAUtil rsaUtil) {
        return new RSAEncryptResponseAspect(rsaUtil);
    }
}