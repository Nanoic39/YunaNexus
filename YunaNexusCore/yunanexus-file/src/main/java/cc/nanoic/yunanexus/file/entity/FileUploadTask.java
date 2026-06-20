package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_upload_task")
public class FileUploadTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private byte[] globalId;
    private Long folderId;
    private String fileName;
    private Long fileSize;
    private String fileExt;
    private String fileMime;
    private Long chunkSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private Integer fileCategory;
    private Integer publicStatus;
    private String serviceName;
    private String oauthAppUuid;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}