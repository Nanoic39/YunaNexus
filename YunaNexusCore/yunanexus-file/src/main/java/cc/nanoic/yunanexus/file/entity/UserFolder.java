package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_folder")
public class UserFolder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String folderUuid;
    private Long userId;
    private Long parentId;
    private String folderName;
    private Integer folderType;
    private String serviceName;
    private String oauthAppUuid;
    private String folderPath;
    private Integer depth;
    private Integer sortNo;
    private Integer status;
    private Integer deleteStage;
    private LocalDateTime deletedAt;
    private LocalDateTime recycleExpireAt;
    private LocalDateTime preDeleteExpireAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}