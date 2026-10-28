package cc.nanoic.yunanexus.file.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 上传限流共享计数（跨实例一致）。
 *
 * <p>用数据库而不是 Redis：file 服务当前没有 Redis 依赖，为限流引入一整套连接池
 * 与运维面不划算；MySQL 的原子 UPSERT 足以承担"每分钟每用户几次"这个量级。
 *
 * @author yunanexus
 */
public interface UploadRateMapper {

    /**
     * 计数 +1 并返回当前窗口内次数。
     *
     * @param key         限流键
     * @param windowStart 窗口起点（毫秒）
     * @return 本窗口累计次数
     */
    @Insert("INSERT INTO upload_rate (rate_key, window_start, hits) VALUES (#{key}, #{windowStart}, 1) "
            + "ON DUPLICATE KEY UPDATE hits = hits + 1")
    int increment(@Param("key") String key, @Param("windowStart") long windowStart);

    /**
     * 读取当前窗口计数。
     *
     * @param key         限流键
     * @param windowStart 窗口起点
     * @return 次数
     */
    @Select("SELECT hits FROM upload_rate WHERE rate_key = #{key} AND window_start = #{windowStart}")
    Integer selectHits(@Param("key") String key, @Param("windowStart") long windowStart);

    /**
     * 清理过期窗口，防止表无界增长。
     *
     * @param before 早于该时间戳的窗口删除
     * @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM upload_rate WHERE window_start < #{before}")
    int purgeBefore(@Param("before") long before);
}
