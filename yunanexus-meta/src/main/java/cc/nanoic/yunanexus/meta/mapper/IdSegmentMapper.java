package cc.nanoic.yunanexus.meta.mapper;

import cc.nanoic.yunanexus.meta.entity.IdSegment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdSegmentMapper extends BaseMapper<IdSegment> {

    /**
     * 乐观锁更新：原子性地领取号段并持久化新步长。<p>
     *
     * 将 {@code max_id} 推进到 {@code newMaxId}，同时把 {@code step} 更新为
     * 动态步长计算结果（供下一次领取使用），{@code version + 1} 保证并发安全。
     *
     * @param bizTag     业务标识
     * @param oldVersion 乐观锁旧版本号
     * @param newMaxId   推进后的 max_id（= 旧 max_id + 步长）
     * @param step       本次持久化的新步长
     * @return 影响行数（1 表示成功，0 表示版本冲突）
     */
    @Update("UPDATE id_segment SET max_id = #{newMaxId}, step = #{step}, version = version + 1 "
            + "WHERE biz_tag = #{bizTag} AND version = #{oldVersion}")
    int updateMaxId(@Param("bizTag") String bizTag,
                    @Param("oldVersion") int oldVersion,
                    @Param("newMaxId") long newMaxId,
                    @Param("step") int step);
}
