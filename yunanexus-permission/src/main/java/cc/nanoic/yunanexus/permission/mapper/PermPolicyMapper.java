package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermPolicy;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ABAC 策略 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermPolicyMapper extends BaseMapper<PermPolicy> {

    /**
     * 查询全部启用策略，按优先级降序（同优先级按 id 升序，保证判定稳定）。
     *
     * @return 启用策略
     */
    @Select("SELECT * FROM perm_policy WHERE status = 1 ORDER BY priority DESC, id ASC")
    List<PermPolicy> selectEnabledOrderByPriority();
}
