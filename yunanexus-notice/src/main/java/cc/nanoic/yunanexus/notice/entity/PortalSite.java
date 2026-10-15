package cc.nanoic.yunanexus.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门户站点配置实体（{@code portal_site}）。<p>
 *
 * 门户内容持久化的站点级元信息，与前端 {@code SiteMeta} 对齐。
 *
 * @author yunanexus
 */
@Data
@TableName("portal_site")
public class PortalSite {

    /** 默认站点标识 */
    public static final String DEFAULT_SITE_KEY = "default";
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 站点标识（默认 default） */
    private String siteKey;

    /** 站点名称 */
    private String name;

    /** 标语 */
    private String tagline;

    /** 简介 */
    private String description;

    /** 版本号 */
    private String version;

    /** 代码仓库地址 */
    private String repositoryUrl;

    /** 页脚文案 */
    private String footerNote;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
