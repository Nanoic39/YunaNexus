package cc.nanoic.yunanexus.auth.mapper;
import cc.nanoic.yunanexus.auth.entity.PersonalApiKey;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface PersonalApiKeyMapper {
    @Select("SELECT user_id FROM auth_user WHERE user_id=#{userId} FOR UPDATE")
    Long lockOwner(long userId);
    @Insert("INSERT INTO auth_personal_api_key(id,user_id,name,token_prefix,token_hash,scopes,created_at,expires_at) VALUES(#{id},#{userId},#{name},#{tokenPrefix},#{tokenHash},#{scopes},#{createdAt},#{expiresAt})")
    void insert(PersonalApiKey key);
    @Select("SELECT * FROM auth_personal_api_key WHERE user_id=#{userId} ORDER BY created_at DESC LIMIT 100")
    List<PersonalApiKey> list(long userId);
    @Select("SELECT * FROM auth_personal_api_key WHERE token_hash=#{hash} AND revoked=0 AND expires_at>NOW()")
    PersonalApiKey find(String hash);
    @Update("UPDATE auth_personal_api_key SET revoked=1 WHERE id=#{id} AND user_id=#{userId}")
    int revoke(@Param("id") String id,@Param("userId") long userId);
    @Update("UPDATE auth_personal_api_key SET last_used_at=NOW() WHERE id=#{id} AND (last_used_at IS NULL OR last_used_at<DATE_SUB(NOW(),INTERVAL 1 MINUTE))")
    void used(String id);
    @Delete("DELETE FROM auth_personal_api_key WHERE user_id=#{userId} AND expires_at<DATE_SUB(NOW(),INTERVAL 30 DAY)")
    void prune(long userId);
}
