package cc.nanoic.yunanexus.common.security.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import cc.nanoic.yunanexus.common.security.annotation.RSAEncryptResponse;
import cc.nanoic.yunanexus.common.security.util.RSAUtil;

@Aspect
public class RSAEncryptResponseAspect {
    private final RSAUtil rsaUtil;

    public RSAEncryptResponseAspect(RSAUtil rsaUtil) {
        this.rsaUtil = rsaUtil;
    }

    @Around("@annotation(rsaEncryptResponse)")
    public Object around(ProceedingJoinPoint joinPoint, RSAEncryptResponse rsaEncryptResponse) throws Throwable {
        Object result = joinPoint.proceed();
        if (result == null)
            return null;
        if (result instanceof String s)
            return rsaUtil.encrypt(s);
        throw new IllegalStateException("@RSAEncryptResponse 当前仅支持 String 返回值");
    }
}
