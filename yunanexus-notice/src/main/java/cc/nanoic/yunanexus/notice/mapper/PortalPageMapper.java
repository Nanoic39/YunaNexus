package cc.nanoic.yunanexus.notice.mapper;

import cc.nanoic.yunanexus.notice.entity.PortalPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 门户页面 Mapper。
 *
 * @author yunanexus
 */
public interface PortalPageMapper extends BaseMapper<PortalPage> {

    /**
     * 按站点标识 + 页面标识查询（站点内页面标识唯一）。
     *
     * @param siteKey 站点标识
     * @param pageId  页面标识
     * @return 页面，不存在返回 {@code null}
     */
    @Select("SELECT * FROM portal_page WHERE site_key = #{siteKey} AND page_id = #{pageId} LIMIT 1")
    PortalPage selectByPageId(@Param("siteKey") String siteKey, @Param("pageId") String pageId);

    /**
     * 按站点标识 + 路由路径查询（取第一个匹配项）。
     *
     * @param siteKey 站点标识
     * @param path    路由路径
     * @return 页面，不存在返回 {@code null}
     */
    @Select("SELECT * FROM portal_page WHERE site_key = #{siteKey} AND path = #{path} "
            + "ORDER BY sort_order ASC, id ASC LIMIT 1")
    PortalPage selectByPath(@Param("siteKey") String siteKey, @Param("path") String path);
}
