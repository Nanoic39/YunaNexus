package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserInfo {
    /**
     * 用户信息表主键id(不对外公开,仅用于系统内部操作)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户Id(关联用户表id字段)
     */
    private Long userId;

    /**
     * 用户昵称(外显)
     */
    private String nickname;

    /**
     * 用户头像文件uuid
     */
    private String avatarUuid;

    /**
     * 用户性别(这个居然是可以自行输入的吗...哈基沃尔玛购物袋，你这家伙..)
     */
    private String gender;

    /**
     * 用户生日
     */
    private LocalDate birthday;

    /**
     * 用户信息更新时间戳
     */
    private LocalDateTime updateTime;



}
