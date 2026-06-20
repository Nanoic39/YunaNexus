package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_storage_quota")
public class FileStorageQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private Long maxSingleFileSize;
    private Long maxTotalStorage;
    private Integer priority;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}