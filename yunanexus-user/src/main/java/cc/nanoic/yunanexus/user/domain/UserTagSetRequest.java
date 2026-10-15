package cc.nanoic.yunanexus.user.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户标签覆盖式设置请求（{@code PUT /api/v1/user/tags}）。<p>
 *
 * 提交的标签集合整体替换当前用户已有标签。
 *
 * @author yunanexus
 */
@Data
public class UserTagSetRequest {

    /** 标签集合（整体替换，最多 20 个） */
    @NotNull(message = "tags 不能为空")
    @Size(max = 20, message = "tags 数量不能超过 20")
    @Valid
    private List<UserTagView> tags = new ArrayList<>();
}
