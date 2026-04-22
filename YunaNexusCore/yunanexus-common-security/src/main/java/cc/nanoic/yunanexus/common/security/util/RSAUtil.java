package cc.nanoic.yunanexus.common.security.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.util.Base64;

import javax.crypto.Cipher;

import cc.nanoic.yunanexus.common.security.constant.RSAConstant;

public class RSAUtil {
    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    /**
     * 构造函数
     * 初始化公钥和私钥
     */
    public RSAUtil(KeyPair keyPair) {
        this.publicKey = keyPair.getPublic();
        this.privateKey = keyPair.getPrivate();
    }

    /**
     * 加密工具
     * 
     * @param plainText 明文内容
     * @return 加密后的Base64编码
     */
    public String encrypt(String plainText) {
        return Base64.getEncoder()
                .encodeToString(process(Cipher.ENCRYPT_MODE, publicKey, plainText.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 解密工具
     * 
     * @param cipherBase64 加密后的Base64编码
     * @return 明文内容
     */
    public String decrypt(String cipherBase64) {
        return new String(process(Cipher.DECRYPT_MODE, privateKey, Base64.getDecoder().decode(cipherBase64)),
                StandardCharsets.UTF_8);
    }

    /**
     * 生成签名
     * 
     * @param plainText 明文内容
     * @return 签名后的Base64编码
     */
    public String sign(String plainText) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(privateKey);
            s.update(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException("RSA签名失败", e);
        }
    }

    /**
     * 验证签名
     * 
     * @param plainText 明文内容
     * @param signBase64 签名后的Base64编码
     * @return 是否验证通过
     */
    public boolean verify(String plainText, String signBase64) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(publicKey);
            s.update(plainText.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            throw new IllegalStateException("RSA验签失败", e);
        }
    }

    /**
     * 处理数据，包括加密和解密
     * 
     * @param mode 模式，ENCRYPT_MODE或DECRYPT_MODE
     * @param key 密钥
     * @param data 待处理数据
     * @return 处理后的数据
     */
    private byte[] process(int mode, Key key, byte[] data) {
        try {
            Cipher c = Cipher.getInstance(RSAConstant.TRANSFORMATION);
            c.init(mode, key);
            int k = ((RSAKey) key).getModulus().bitLength() / 8;
            int block = mode == Cipher.ENCRYPT_MODE ? (k - 11) : k;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int off = 0; off < data.length; off += block) {
                int len = Math.min(block, data.length - off);
                out.write(c.doFinal(data, off, len));
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("RSA处理失败", e);
        }
    }

}
