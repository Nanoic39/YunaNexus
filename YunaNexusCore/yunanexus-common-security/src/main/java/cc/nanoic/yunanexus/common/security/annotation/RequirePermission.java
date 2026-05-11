package cc.nanoic.yunanexus.common.security.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 权限编码列表
     * resources.code
     */
    String[] value();

    /**
     * 是否要求匹配全部权限，默认存在一种权限就允许访问
     * is True ? AND : OR;
     */
    boolean allMatch() default false;
}
