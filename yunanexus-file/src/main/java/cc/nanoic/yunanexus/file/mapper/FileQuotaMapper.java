package cc.nanoic.yunanexus.file.mapper;

import cc.nanoic.yunanexus.file.entity.FileQuota;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 配额 Mapper。
 *
 * <p>增减用 SQL 原子操作而不是"读-改-写"：并发上传时读改写会丢计数，
 * 而配额一旦少算就是可被利用的漏洞。
 *
 * @author yunanexus
 */
public interface FileQuotaMapper extends BaseMapper<FileQuota> {

    /**
     * 原子累加用量（行不存在则创建）。
     *
     * @param userId    用户 ID
     * @param bytes     新增字节
     * @param fileCount 新增文件数
     * @return 影响行数
     */
    @Insert("INSERT INTO file_quota (user_id, used_bytes, file_count) VALUES (#{userId}, #{bytes}, #{fileCount}) "
            + "ON DUPLICATE KEY UPDATE used_bytes = used_bytes + #{bytes}, file_count = file_count + #{fileCount}")
    int increment(@Param("userId") long userId, @Param("bytes") long bytes, @Param("fileCount") long fileCount);

    /**
     * 原子扣减用量（不会减成负数）。
     *
     * @param userId    用户 ID
     * @param bytes     扣减字节
     * @param fileCount 扣减文件数
     * @return 影响行数
     */
    @Update("UPDATE file_quota SET used_bytes = GREATEST(0, used_bytes - #{bytes}), "
            + "file_count = GREATEST(0, file_count - #{fileCount}) WHERE user_id = #{userId}")
    int decrement(@Param("userId") long userId, @Param("bytes") long bytes, @Param("fileCount") long fileCount);

    /**
     * 校正用量（对账任务使用）。
     *
     * @param userId    用户 ID
     * @param bytes     真实用量
     * @param fileCount 真实文件数
     * @return 影响行数
     */
    @Update("UPDATE file_quota SET used_bytes = #{bytes}, file_count = #{fileCount} WHERE user_id = #{userId}")
    int correct(@Param("userId") long userId, @Param("bytes") long bytes, @Param("fileCount") long fileCount);
}
