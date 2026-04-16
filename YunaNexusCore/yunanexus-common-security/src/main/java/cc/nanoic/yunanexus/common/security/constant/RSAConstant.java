package cc.nanoic.yunanexus.common.security.constant;

/**
 * RSA常量定义
 */
public final class RSAConstant {

    private RSAConstant() {
    }

    /**
     * RSA算法
     */
    public static final String ALGORITHM = "RSA";

    /**
     * RSA填充模式
     */
    public static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /**
     * 密钥位数
     */
    public static final int KEY_SIZE = 2048;

    /**
     * 密钥文件根路径
     */
    public static final String SECRET_DIR = "./rsa/";

    

    /**
     * 公钥文件路劲
     */
    public static final String PUBLIC_KEY_FILENAME = "rsa.pub";

    /**
     * 私钥文件路径
     */
    public static final String PRIVATE_KEY_FILENAME = "rsa";
}
