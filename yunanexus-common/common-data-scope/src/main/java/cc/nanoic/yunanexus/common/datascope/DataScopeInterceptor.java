package cc.nanoic.yunanexus.common.datascope;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.Connection;

/**
 * 数据权限拦截器（L4 行级）。<p>
 *
 * 拦截 {@link StatementHandler#prepare}，当「当前线程存在数据范围上下文」且「Mapper 方法标注了
 * {@link DataScope}」时，用 JSqlParser 把数据范围条件 AND 进原 WHERE 子句。<p>
 *
 * 未标注 {@link DataScope} 的 Mapper 方法不受影响；非简单 SELECT（子查询 / UNION 等）跳过注入并告警。
 *
 * @author yunanexus
 */
@Slf4j
@Component
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
public class DataScopeInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        DataScopeContext.Scope scope = DataScopeContext.get();
        if (scope == null || scope.all()) {
            return invocation.proceed();
        }

        MetaObject meta = SystemMetaObject.forObject(invocation.getTarget());
        MappedStatement mappedStatement = (MappedStatement) meta.getValue("delegate.mappedStatement");
        DataScope dataScope = resolveAnnotation(mappedStatement.getId());
        if (dataScope == null) {
            return invocation.proceed();
        }

        String condition = DataScopeSqlBuilder.build(scope, dataScope.deptColumn(), dataScope.createByColumn());
        if (condition == null) {
            return invocation.proceed();
        }

        BoundSql boundSql = (BoundSql) meta.getValue("delegate.boundSql");
        String rewritten = rewrite(boundSql.getSql(), condition);
        if (rewritten != null) {
            meta.setValue("delegate.boundSql.sql", rewritten);
        }
        return invocation.proceed();
    }

    /**
     * 解析 Mapper 方法上的 {@link DataScope}。
     *
     * @param mappedStatementId 形如 {@code cc.nanoic...XxxMapper.methodName}
     */
    private DataScope resolveAnnotation(String mappedStatementId) {
        int idx = mappedStatementId.lastIndexOf('.');
        if (idx <= 0 || idx == mappedStatementId.length() - 1) {
            return null;
        }
        String className = mappedStatementId.substring(0, idx);
        String methodName = mappedStatementId.substring(idx + 1);
        try {
            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                    return method.getAnnotation(DataScope.class);
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("数据权限：无法解析 Mapper 类 {}", className);
        }
        return null;
    }

    /**
     * 将数据范围条件 AND 进 WHERE 子句（两侧均加括号，避免 OR 优先级歧义）。
     *
     * @return 改写后的 SQL；无法改写时返回 {@code null}
     */
    String rewrite(String sql, String condition) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select) || !(select.getSelectBody() instanceof PlainSelect plainSelect)) {
                log.warn("数据权限：SQL 非简单 SELECT，跳过注入");
                return null;
            }
            Expression scopeExpression = new Parenthesis(CCJSqlParserUtil.parseCondExpression(condition));
            Expression where = plainSelect.getWhere();
            plainSelect.setWhere(where == null
                    ? scopeExpression
                    : new AndExpression(new Parenthesis(where), scopeExpression));
            return select.toString();
        } catch (JSQLParserException e) {
            log.warn("数据权限：SQL 解析失败，跳过注入", e);
            return null;
        }
    }
}
