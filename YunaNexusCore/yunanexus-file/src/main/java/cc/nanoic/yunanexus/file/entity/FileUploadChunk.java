package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_upload_chunk")
public class FileUploadChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private Integer chunkIndex;
    private Long chunkSize;
    private String etag;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}