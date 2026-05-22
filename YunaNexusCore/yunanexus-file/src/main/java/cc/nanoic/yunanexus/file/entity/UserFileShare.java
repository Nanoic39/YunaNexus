package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_file_share")
public class UserFileShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shareUuid;
    private String shareCode;
    private Long userId;
    private String fileUuid;
    private String extractCode;
    private Integer viewAuthMode;
    private Integer downloadAuthMode;
    private Long maxDownloadCount;
    private Long downloadCount;
    private Long viewCount;
    private Integer status;
    private LocalDateTime expireAt;
    private LocalDateTime lastViewedAt;
    private LocalDateTime lastDownloadedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
