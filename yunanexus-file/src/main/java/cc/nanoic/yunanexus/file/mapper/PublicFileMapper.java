package cc.nanoic.yunanexus.file.mapper;

import org.apache.ibatis.annotations.*;

/** Publication records live in physical database zero; content remains in its original shard. */
public interface PublicFileMapper {
    record Publication(long fileId, long userId, String token) {}

    @Select("SELECT file_id AS fileId,user_id AS userId,token FROM file_publication WHERE file_id=#{fileId} AND user_id=#{userId}")
    Publication find(@Param("fileId") long fileId, @Param("userId") long userId);

    @Select("SELECT file_id AS fileId,user_id AS userId,token FROM file_publication WHERE token=#{token}")
    Publication resolve(@Param("token") String token);

    @Insert("INSERT INTO file_publication(file_id,user_id,token) VALUES(#{fileId},#{userId},#{token}) ON DUPLICATE KEY UPDATE token=token")
    int publish(@Param("fileId") long fileId,@Param("userId") long userId,@Param("token") String token);

    @Delete("DELETE FROM file_publication WHERE file_id=#{fileId} AND user_id=#{userId}")
    int revoke(@Param("fileId") long fileId,@Param("userId") long userId);
}
