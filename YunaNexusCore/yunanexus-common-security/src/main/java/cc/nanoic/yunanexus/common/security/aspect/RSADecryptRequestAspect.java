package cc.nanoic.yunanexus.common.security.aspect;

import cc.nanoic.yunanexus.common.security.annotation.RSADecryptRequest;
import cc.nanoic.yunanexus.common.security.util.RSAUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

@Aspect
public class RSADecryptRequestAspect {
    private final RSAUtil rsaUtil;

    public RSADecryptRequestAspect(RSAUtil rsaUtil) {
        this.rsaUtil = rsaUtil;
    }

    @Around("@annotation(rsaDecryptRequest)")
    public Object around(ProceedingJoinPoint joinPoint, RSADecryptRequest rsaDecryptRequest) throws Throwable {
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String s && !s.isBlank())
                args[i] = rsaUtil.decrypt(s);
        }
        return joinPoint.proceed(args);
    }
}