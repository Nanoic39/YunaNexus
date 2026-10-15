package cc.nanoic.yunanexus.notice.mapper;

import cc.nanoic.yunanexus.notice.entity.PortalSite;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 门户站点配置 Mapper。
 *
 * @author yunanexus
 */
public interface PortalSiteMapper extends BaseMapper<PortalSite> {

    /**
     * 按站点标识查询（站点标识唯一）。
     *
     * @param siteKey 站点标识
     * @return 站点配置，不存在返回 {@code null}
     */
    @Select("SELECT * FROM portal_site WHERE site_key = #{siteKey} LIMIT 1")
    PortalSite selectBySiteKey(@Param("siteKey") String siteKey);
}
