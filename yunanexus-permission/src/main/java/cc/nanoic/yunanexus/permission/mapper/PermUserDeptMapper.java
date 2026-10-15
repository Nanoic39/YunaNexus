package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermUserDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户-部门绑定 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermUserDeptMapper extends BaseMapper<PermUserDept> {

    /**
     * 查询用户所属部门 ID。
     *
     * @param userId 全局用户 ID
     * @return 部门 ID；未绑定时返回 {@code null}
     */
    @Select("SELECT dept_id FROM perm_user_dept WHERE user_id = #{userId}")
    Long selectDeptIdByUserId(@Param("userId") long userId);
}
