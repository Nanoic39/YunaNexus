package cc.nanoic.yunanexus.common.security.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import cc.nanoic.yunanexus.common.security.config.RSAProperties;

public final class RSAKeyUtil {
    private RSAKeyUtil() {}

    /**
     * 加载或生成RSA密钥对
     * @param properties
     * @return
     */
    public static KeyPair loadOrGenerate(RSAProperties properties) {
        try {
            // 获取路径
            Path secretDir = Path.of(properties.getSecretDir());
            Path publicKeyPath = secretDir.resolve(properties.getPublicKeyFileName());
            Path privateKeyPath = secretDir.resolve(properties.getPrivateKeyFileName());

            // 检测RSA密钥文件是否存在
            if (!Files.exists(publicKeyPath) || !Files.exists(privateKeyPath)) {
                // 若不存在则检测是否开启自动生成
                if (!properties.isAutoGenerate()) { // 未开启自动生成密钥对
                    throw new IllegalArgumentException("RSA密钥文件不存在，且未开启自动生成");
                } else { // 已开启自动生成密钥对功能
                    // TODO: 调用生成密钥对方法

                }
                
            }
        } catch (Exception e) {
            throw new RuntimeException("RSA密钥初始化失败", e);
        }
    }

    // TODO: [私有方法]生成并保存密钥对
}
