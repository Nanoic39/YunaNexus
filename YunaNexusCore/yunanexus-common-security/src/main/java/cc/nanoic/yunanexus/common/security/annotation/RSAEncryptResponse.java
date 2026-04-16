package cc.nanoic.yunanexus.common.security.annotation;

import java.lang.annotation.*;

/**
 * RSA 加密响应注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RSAEncryptResponse {
}