package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

@Data
@TableName("Users")
public class Users {
    /**
     * 用户主键id(不对外公开,仅用于系统内部操作)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户唯一标识(外显,用于系统内外传递用户信息)
     */
    private String uuid;

    /**
     * 用户名(唯一,用于用户登录)
     */
    private String username;

    /**
     * 密码(存储时使用Argon2/BCrypt加密存储)
     */
    private String password;

    /**
     * 电子邮箱(唯一,可用于登录和接收通知)
     */
    private String email;

    /**
     * 帐号状态(0：注销，1：正常，2：封禁/冻结)
     */
    private Integer status;

    /**
     * 账号创建时间戳
     */
    private Timestamp create_time;

    /**
     * 账号更新时间戳
     */
    private Timestamp update_time;
}
