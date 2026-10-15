package cc.nanoic.yunanexus.user.domain;

import cc.nanoic.yunanexus.user.entity.UserTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户标签视图（自助接口读写体）。<p>
 *
 * 既是查询返回元素，也是覆盖式设置请求的元素。
 *
 * @author yunanexus
 */
@Data
public class UserTagView {

    /** 标签编码（用户内唯一） */
    @NotBlank(message = "tagCode 不能为空")
    @Size(max = 32, message = "tagCode 长度不能超过 32")
    private String tagCode;

    /** 标签名称 */
    @NotBlank(message = "tagName 不能为空")
    @Size(max = 32, message = "tagName 长度不能超过 32")
    private String tagName;

    /** 标签颜色（前端展示） */
    @Size(max = 16, message = "color 长度不能超过 16")
    private String color;

    public static UserTagView from(UserTag tag) {
        UserTagView view = new UserTagView();
        view.setTagCode(tag.getTagCode());
        view.setTagName(tag.getTagName());
        view.setColor(tag.getColor());
        return view;
    }
}
