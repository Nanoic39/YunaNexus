package cc.nanoic.yunanexus.user.entity.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateUserInfoDTO {
    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户性别
     */
    private String gender;

    /**
     * 用户生日
     */
    private LocalDate birthday;
}