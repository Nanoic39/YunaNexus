package cc.nanoic.yunanexus.user.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户徽章授予请求（内部接口 {@code POST /internal/user/badges/grant}）。<p>
 *
 * 徽章由系统 / 运营授予（如行为分析命中成就），用户端只读；同一 {@code userId + badgeCode}
 * 重复授予为幂等更新。
 *
 * @author yunanexus
 */
@Data
public class UserBadgeGrantRequest {

    /** 目标用户 ID */
    @NotNull(message = "userId 不能为空")
    private Long userId;

    /** 徽章编码（用户内唯一） */
    @NotBlank(message = "badgeCode 不能为空")
    @Size(max = 64, message = "badgeCode 长度不能超过 64")
    private String badgeCode;

    /** 徽章名称 */
    @NotBlank(message = "badgeName 不能为空")
    @Size(max = 64, message = "badgeName 长度不能超过 64")
    private String badgeName;

    /** 徽章等级（1~9），缺省 1 */
    private Integer badgeLevel;

    /** 徽章描述 */
    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    /** 徽章图标 URL */
    @Size(max = 512, message = "icon 长度不能超过 512")
    private String icon;
}
