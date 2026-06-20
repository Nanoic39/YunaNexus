package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_storage_node")
public class FileStorageNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nodeCode;
    private String nodeName;
    private Integer storageVendor;
    private String endpoint;
    private String bucketName;
    private String region;
    private Integer weight;
    private Integer healthStatus;
    private Integer status;
    private LocalDateTime lastHeartbeatTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}