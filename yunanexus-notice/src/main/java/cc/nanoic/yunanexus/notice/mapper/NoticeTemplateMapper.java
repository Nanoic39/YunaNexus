package cc.nanoic.yunanexus.notice.mapper;

import cc.nanoic.yunanexus.notice.entity.NoticeTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通知模板 Mapper。
 *
 * @author yunanexus
 */
public interface NoticeTemplateMapper extends BaseMapper<NoticeTemplate> {

    /**
     * 按模板编码查询（编码全局唯一）。
     *
     * @param templateCode 模板编码
     * @return 模板，不存在返回 {@code null}
     */
    @Select("SELECT * FROM notice_template WHERE template_code = #{templateCode}")
    NoticeTemplate selectByCode(@Param("templateCode") String templateCode);

    /**
     * 分页查询模板。
     *
     * @param offset 起始偏移
     * @param limit  每页大小
     * @return 模板列表，按主键倒序
     */
    @Select("SELECT * FROM notice_template ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<NoticeTemplate> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计模板总数。
     *
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM notice_template")
    long countAll();
}
