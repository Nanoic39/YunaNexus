package cc.nanoic.yunanexus.user.entity.VO;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserInfoVO {
    /**
     * 用户唯一标识(外显,用于系统内外传递用户信息)
     */
    private String uuid;

    /**
     * 用户昵称(外显)
     */
    private String nickname;

    /**
     * 用户头像文件uuid
     */
    private String avatarUuid;

    /**
     * 用户性别
     */
    private String gender;

    /**
     * 用户生日
     */
    private LocalDate birthday;

    /**
     * 账号创建时间戳
     */
    private LocalDateTime createTime;

    /**
     * 用户信息更新时间戳
     */
    private LocalDateTime updateTime;
}
