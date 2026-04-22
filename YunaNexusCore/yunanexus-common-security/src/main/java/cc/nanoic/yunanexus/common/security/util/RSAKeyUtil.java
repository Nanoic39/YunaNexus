package cc.nanoic.yunanexus.common.security.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import cc.nanoic.yunanexus.common.security.config.RSAProperties;
import cc.nanoic.yunanexus.common.security.constant.RSAConstant;

public final class RSAKeyUtil {
    private RSAKeyUtil() {
    }

    /**
     * 加载或生成RSA密钥对
     * 
     * @param properties RSA基本配置
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
                if (!properties.isAutoGenerate()) { // 未开启自动生成密钥对直接抛出异常
                    throw new IllegalArgumentException("RSA密钥文件不存在，且未开启自动生成");
                }
                // 否则自动生成密钥对
                generateAndSaveKeyPair(properties, secretDir, publicKeyPath, privateKeyPath);
            }
            // 密钥生成完毕，重新尝试加载
            PublicKey publicKey = readPublicKey(publicKeyPath);
            PrivateKey privateKey = readPrivateKey(privateKeyPath);
            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            throw new RuntimeException("RSA密钥初始化失败", e);
        }
    }

    /**
     * 生成并保存RSA密钥对
     * 
     * @param properties     RSA配置
     * @param secretDir      密钥文件根路径
     * @param publicKeyPath  公钥文件路径
     * @param privateKeyPath 私钥文件路径
     * @throws Exception 生成密钥对失败时抛出异常
     */
    private static void generateAndSaveKeyPair(RSAProperties properties, Path secretDir, Path publicKeyPath,
            Path privateKeyPath) throws Exception {
        // 创建文件目录
        Files.createDirectories(secretDir);
        // 初始化密钥对生成器
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSAConstant.ALGORITHM);
        keyPairGenerator.initialize(properties.getKeySize(), new SecureRandom());
        // 生成密钥对
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        // 写入文件
        Files.writeString(publicKeyPath, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                StandardCharsets.UTF_8);
        Files.writeString(privateKeyPath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                StandardCharsets.UTF_8);
    }

    /**
     * 读取公钥
     * 
     * @param publicKeyPath 公钥文件路径
     * @return 公钥密钥内容
     * @throws Exception 读取公钥失败时抛出异常
     */
    private static PublicKey readPublicKey(Path publicKeyPath) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(Files.readString(publicKeyPath).trim());
        return KeyFactory.getInstance(RSAConstant.ALGORITHM).generatePublic(new X509EncodedKeySpec(bytes));
    }

    /**
     * 读取私钥
     * 
     * @param privateKeyPath 私钥文件路径
     * @return 私钥密钥内容
     * @throws Exception 读取私钥失败时抛出异常
     */
    private static PrivateKey readPrivateKey(Path privateKeyPath) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(Files.readString(privateKeyPath).trim());
        return KeyFactory.getInstance(RSAConstant.ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }
}
