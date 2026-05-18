package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_object")
public class FileObject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectUuid;
    private String objectKey;
    private Long primaryNodeId;
    private Integer storageType;
    private String fileHash;
    private String hashAlgo;
    private Long fileSize;
    private String fileExt;
    private String fileMime;
    private Integer isEncrypted;
    private Integer compressionType;
    private Long refCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}