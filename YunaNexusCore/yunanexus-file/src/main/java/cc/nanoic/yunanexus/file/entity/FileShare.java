package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_share")
public class FileShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shareUuid;
    private String shareCode;
    private byte[] globalId;
    private String extractCode;
    private Integer needLogin;
    private Integer allowPreview;
    private Long maxViewCount;
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