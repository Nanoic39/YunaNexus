package cc.nanoic.yunanexus.common.security.annotation;

import java.lang.annotation.*;

/**
 * RSA 解密请求注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RSADecryptRequest {
}