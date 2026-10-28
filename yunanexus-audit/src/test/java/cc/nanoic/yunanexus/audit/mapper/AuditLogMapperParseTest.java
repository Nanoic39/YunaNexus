package cc.nanoic.yunanexus.audit.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperAnnotationBuilder;
import org.junit.jupiter.api.Test;

/**
 * 守住"注解 SQL 能被 MyBatis 解析"这条底线。
 *
 * <p>这不需要数据库：解析只依赖 Configuration 与 Mapper 接口。之所以要有它，
 * 是因为注解 SQL 的错误在编译期和普通单测里完全隐形，只会在服务启动时炸掉容器。
 */
class AuditLogMapperParseTest {

    @Test
    void annotationsParse() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        new MybatisMapperAnnotationBuilder(configuration, AuditLogMapper.class).parse();
    }
}
