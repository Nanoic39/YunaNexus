package cc.nanoic.yunanexus.file.mapper;
import cc.nanoic.yunanexus.file.entity.FileShare;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** 分享索引固定存于文件中心物理库 0；所有者条件必须在 SQL 中生效。 */
public interface FileShareMapper {
    @Insert("INSERT INTO file_share(share_id,user_id,file_id,file_name,code_hash,created_at,expires_at) VALUES(#{shareId},#{userId},#{fileId},#{fileName},#{codeHash},#{createdAt},#{expiresAt})")
    void insert(FileShare share);
    @Select("SELECT * FROM file_share WHERE user_id=#{userId} ORDER BY created_at DESC LIMIT 200")
    List<FileShare> list(long userId);
    @Update("UPDATE file_share SET revoked=1 WHERE file_id=#{fileId} AND user_id=#{userId}")
    int revokeFile(@Param("fileId") long fileId,@Param("userId") long userId);
    @Select("SELECT * FROM file_share WHERE share_id=#{shareId}")
    FileShare find(String shareId);
    @Update("UPDATE file_share SET revoked=1 WHERE share_id=#{shareId} AND user_id=#{userId}")
    int revoke(@Param("shareId") String shareId,@Param("userId") long userId);
    @Update("UPDATE file_share SET failed_attempts=IF(failure_window IS NULL OR failure_window<DATE_SUB(NOW(),INTERVAL 15 MINUTE),1,failed_attempts+1), failure_window=IF(failure_window IS NULL OR failure_window<DATE_SUB(NOW(),INTERVAL 15 MINUTE),NOW(),failure_window) WHERE share_id=#{shareId} AND (failed_attempts<5 OR failure_window IS NULL OR failure_window<DATE_SUB(NOW(),INTERVAL 15 MINUTE))")
    int failure(String shareId);
    @Update("UPDATE file_share SET failed_attempts=0,failure_window=NULL WHERE share_id=#{shareId}")
    void resetFailures(String shareId);
    @Delete("DELETE FROM file_share WHERE user_id=#{userId} AND expires_at<DATE_SUB(NOW(),INTERVAL 30 DAY)")
    void prune(long userId);
    @Select("SELECT COUNT(*) FROM file_share WHERE user_id=#{userId}")
    long count(long userId);
}
