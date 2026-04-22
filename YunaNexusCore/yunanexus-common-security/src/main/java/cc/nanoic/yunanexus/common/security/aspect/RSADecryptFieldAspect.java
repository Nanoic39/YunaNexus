package cc.nanoic.yunanexus.common.security.aspect;

import cc.nanoic.yunanexus.common.security.annotation.RSADecryptField;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import cc.nanoic.yunanexus.common.security.util.RSAUtil;

import java.lang.reflect.Field;

/**
 * RSA解密字段切面
 * 用于在方法执行前对被注解的字段进行RSA解密
 * 只在实体配置无法生效
 * 需要接口配合RSADecryptRequest才会生效
 * 且字段必须为String类型 :(
 */
@Aspect
public class RSADecryptFieldAspect {

    private final RSAUtil rsaUtil;

    public RSADecryptFieldAspect(RSAUtil rsaUtil) {
        this.rsaUtil = rsaUtil;
    }

    @Around("@annotation(cc.nanoic.yunanexus.common.security.annotation.RSADecryptRequest)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        for (Object arg : args) {
            decryptMarkedFields(arg);
        }
        return pjp.proceed(args);
    }

    private void decryptMarkedFields(Object target) {
        if (target == null)
            return;
        Class<?> clazz = target.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(RSADecryptField.class))
                continue;
            if (!String.class.equals(field.getType()))
                continue;

            try {
                field.setAccessible(true);
                String cipher = (String) field.get(target);
                if (cipher == null || cipher.isBlank())
                    continue;
                String plain = rsaUtil.decrypt(cipher);
                field.set(target, plain);
            } catch (Exception e) {
                throw new IllegalStateException("RSA字段解密失败: " + field.getName(), e);
            }
        }
    }
}