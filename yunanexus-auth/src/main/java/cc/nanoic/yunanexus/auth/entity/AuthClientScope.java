package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_client_scope")
public class AuthClientScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String clientCode;
    private Long permissionId;
    private LocalDateTime createTime;
}
