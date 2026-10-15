package cc.nanoic.yunanexus.notice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 门户页面保存请求（管理端，新增 / 修改共用）。<p>
 *
 * {@code sections} 为页面区块数组，落库时序列化为 JSON。
 *
 * @author yunanexus
 */
@Data
public class PortalPageSaveRequest {

    /** 页面标识（站点内唯一，前端路由 key） */
    @NotBlank(message = "页面标识不能为空")
    @Size(max = 64, message = "页面标识长度不能超过 64")
    private String id;

    @NotBlank(message = "路由路径不能为空")
    @Size(max = 255, message = "路由路径长度不能超过 255")
    private String path;

    @NotBlank(message = "页面标题不能为空")
    @Size(max = 255, message = "页面标题长度不能超过 255")
    private String title;

    @Size(max = 512, message = "页面描述长度不能超过 512")
    private String description;

    /** 是否在门户导航展示 */
    private Boolean nav;

    private List<PortalSection> sections;

    /** 排序号（升序） */
    private Integer sortOrder;

    /** 状态：1-已发布 0-草稿；缺省为已发布 */
    private Integer status;
}
