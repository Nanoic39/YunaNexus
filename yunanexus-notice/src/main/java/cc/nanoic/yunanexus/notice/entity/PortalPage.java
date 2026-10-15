package cc.nanoic.yunanexus.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门户页面实体（{@code portal_page}）。<p>
 *
 * 页面级内容，{@code sections}（sections→elements→items）以 JSON 字符串落库，
 * 与前端 {@code ContentPage} 对齐。
 *
 * @author yunanexus
 */
@Data
@TableName("portal_page")
public class PortalPage {

    /** 状态：已发布 */
    public static final int STATUS_PUBLISHED = 1;
    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** nav：展示 */
    public static final int NAV_TRUE = 1;
    /** nav：不展示 */
    public static final int NAV_FALSE = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 站点标识 */
    private String siteKey;

    /** 页面标识（站点内唯一） */
    private String pageId;

    /** 路由路径 */
    private String path;

    /** 页面标题 */
    private String title;

    /** 页面描述 */
    private String description;

    /** 是否在门户导航展示：1-是 0-否 */
    private Integer nav;

    /** 区块内容（JSON 数组） */
    private String sections;

    /** 排序号（升序） */
    private Integer sortOrder;

    /** 状态：1-已发布 0-草稿 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
