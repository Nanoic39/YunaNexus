package cc.nanoic.yunanexus.notice.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 通知去重窗口。
 *
 * <p>依赖主键冲突判断"本窗口已投递"：插入成功=首次投递，冲突=重复。
 * 比"先查再写"少一次往返，且在并发下不会出现双发。
 *
 * @author yunanexus
 */
public interface NoticeDedupMapper {

    /**
     * 尝试占用窗口。
     *
     * @return 1 表示首次（应投递）；0 表示窗口内已投递过（应跳过）
     */
    @Insert("INSERT IGNORE INTO notice_dedup (user_id, category, window_start) "
            + "VALUES (#{userId}, #{category}, #{windowStart})")
    int tryAcquire(@Param("userId") long userId,
                   @Param("category") String category,
                   @Param("windowStart") long windowStart);

    /**
     * 清理过期窗口。
     */
    @Delete("DELETE FROM notice_dedup WHERE window_start < #{before}")
    int purgeBefore(@Param("before") long before);
}
