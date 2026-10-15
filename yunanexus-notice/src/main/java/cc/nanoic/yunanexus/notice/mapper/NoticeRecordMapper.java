package cc.nanoic.yunanexus.notice.mapper;

import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知记录 Mapper。
 *
 * @author yunanexus
 */
public interface NoticeRecordMapper extends BaseMapper<NoticeRecord> {

    /**
     * 按全局通知 ID 查询记录。
     *
     * @param noticeId 全局通知 ID
     * @return 通知记录，不存在返回 {@code null}
     */
    @Select("SELECT * FROM notice_record WHERE notice_id = #{noticeId}")
    NoticeRecord selectByNoticeId(@Param("noticeId") long noticeId);

    /**
     * 分页查询用户站内信收件箱（已发送 / 已读）。
     *
     * @param receiver 接收方（用户 ID 的字符串形式）
     * @param offset   起始偏移
     * @param limit    每页大小
     * @return 站内信列表，按时间倒序
     */
    @Select("SELECT * FROM notice_record WHERE channel = 1 AND receiver = #{receiver} "
            + "AND status IN (1, 3) ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<NoticeRecord> selectInboxPage(@Param("receiver") String receiver,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 统计用户站内信收件箱总数（过滤条件与 {@link #selectInboxPage} 一致）。
     *
     * @param receiver 接收方（用户 ID 的字符串形式）
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM notice_record WHERE channel = 1 AND receiver = #{receiver} "
            + "AND status IN (1, 3)")
    long countInbox(@Param("receiver") String receiver);

    /**
     * 分页查询通知记录（可按模板编码 / 接收方 / 状态过滤）。
     *
     * @param templateCode 模板编码，空表示不过滤
     * @param receiver     接收方，空表示不过滤
     * @param status       状态，null 表示不过滤
     * @param offset       起始偏移
     * @param limit        每页大小
     * @return 记录列表，按主键倒序
     */
    @Select("<script>"
            + "SELECT * FROM notice_record"
            + "<where>"
            + "<if test='templateCode != null and templateCode != \"\"'> AND template_code = #{templateCode}</if>"
            + "<if test='receiver != null and receiver != \"\"'> AND receiver = #{receiver}</if>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</where>"
            + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<NoticeRecord> selectRecordPage(@Param("templateCode") String templateCode,
                                        @Param("receiver") String receiver,
                                        @Param("status") Integer status,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 统计通知记录总数（过滤条件与 {@link #selectRecordPage} 一致）。
     *
     * @param templateCode 模板编码，空表示不过滤
     * @param receiver     接收方，空表示不过滤
     * @param status       状态，null 表示不过滤
     * @return 记录数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM notice_record"
            + "<where>"
            + "<if test='templateCode != null and templateCode != \"\"'> AND template_code = #{templateCode}</if>"
            + "<if test='receiver != null and receiver != \"\"'> AND receiver = #{receiver}</if>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</where>"
            + "</script>")
    long countRecords(@Param("templateCode") String templateCode,
                      @Param("receiver") String receiver,
                      @Param("status") Integer status);

    /**
     * 标记站内信已读。
     *
     * @return 受影响行数（0 表示记录不存在 / 非本人 / 状态不允许）
     */
    @Update("UPDATE notice_record SET status = 3, update_time = NOW() "
            + "WHERE notice_id = #{noticeId} AND channel = 1 AND receiver = #{receiver} AND status = 1")
    int markRead(@Param("noticeId") long noticeId, @Param("receiver") String receiver);
}
