package cc.nanoic.yunanexus.common.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限标记（标注在 Mapper 查询方法上）。<p>
 *
 * 被标注的方法执行时，若当前线程存在数据范围上下文（{@link DataScopeContext}），
 * 拦截器会把数据范围条件注入 SQL 的 WHERE 子句：
 * {@code AND (deptColumn IN (...) OR createByColumn = <当前用户>)}。<p>
 *
 * 列名必须带上 SQL 中的表别名（如 {@code o.dept_id}），否则多表关联时会歧义。
 *
 * @author yunanexus
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DataScope {

    /** 部门列名（含表别名），用于「本部门 / 本部门及以下 / 自定义」范围 */
    String deptColumn() default "dept_id";

    /** 创建人列名（含表别名），用于「仅本人」范围 */
    String createByColumn() default "create_by";
}
