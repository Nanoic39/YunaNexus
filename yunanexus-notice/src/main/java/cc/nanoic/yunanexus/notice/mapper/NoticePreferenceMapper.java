package cc.nanoic.yunanexus.notice.mapper;

import cc.nanoic.yunanexus.notice.entity.NoticePreference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通知偏好 Mapper。
 *
 * @author yunanexus
 */
public interface NoticePreferenceMapper extends BaseMapper<NoticePreference> {

    /**
     * 取某用户在某通道下的全部偏好（含通配项）。
     *
     * @param userId  用户 ID
     * @param channel 通道码
     * @return 偏好列表
     */
    @Select("SELECT * FROM notice_preference WHERE user_id = #{userId} AND channel = #{channel}")
    List<NoticePreference> selectByUserChannel(@Param("userId") long userId,
                                               @Param("channel") int channel);

    /**
     * 取某用户的全部偏好。
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    @Select("SELECT * FROM notice_preference WHERE user_id = #{userId}")
    List<NoticePreference> selectByUser(@Param("userId") long userId);
}
