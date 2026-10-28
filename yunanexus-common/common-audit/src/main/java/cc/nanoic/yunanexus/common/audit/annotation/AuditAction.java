package cc.nanoic.yunanexus.common.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务动作审计注解。<p>
 *
 * 打在 Controller 方法上，声明"这个接口在业务上是什么动作"。没有它，审计只能记录
 * "谁 POST 了 /api/v1/users/7"，有了它才能记录"谁把 7 号用户的角色改成了管理员"——
 * 出事后能回答问题的是后者。
 *
 * <p>注解只提供语义，采集链路（拦截器、异步上报、去重合并）由 common-audit 统一完成，
 * 业务代码不需要写任何 try/catch 或 http 调用。
 *
 * @author yunanexus
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AuditAction {

    /** 业务模块，如 user / permission / file */
    String module();

    /** 业务动作，如 CREATE / DELETE / GRANT / EXPORT */
    String action();

    /**
     * 风险级别，用于告警筛选。
     *
     * <p>取值 NONE / HIGH / CRITICAL，与审计服务侧的风险判定口径一致；
     * 留空表示交给服务端规则判定，注解只补充语义不强制分级。
     */
    String risk() default "";

    /** 是否采集请求参数（会做敏感字段脱敏） */
    boolean captureParams() default true;

    /** 是否由业务方通过 {@code AuditCollectContext.result(...)} 补充执行结果摘要 */
    boolean captureResult() default true;
}
