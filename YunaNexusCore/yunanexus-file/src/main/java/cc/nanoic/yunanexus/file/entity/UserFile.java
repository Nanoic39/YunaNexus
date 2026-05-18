package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_file")
public class UserFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileUuid;
    private Long userId;
    private Long folderId;
    private Long objectId;
    private Integer fileCategory;
    private Integer publicStatus;
    private String serviceName;
    private String oauthAppUuid;
    private String originName;
    private String fileName;
    private Long fileSize;
    private String fileExt;
    private String fileMime;
    private String fileHash;
    private Integer versionNo;
    private Integer status;
    private Integer deleteStage;
    private LocalDateTime deletedAt;
    private LocalDateTime recycleExpireAt;
    private LocalDateTime preDeleteExpireAt;
    private Long deletedBy;
    private Long purgedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}