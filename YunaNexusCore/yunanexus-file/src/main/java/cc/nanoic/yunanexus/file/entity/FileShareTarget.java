package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_share_target")
public class FileShareTarget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shareId;
    private Integer targetType;
    private String targetUuid;
    private Integer sortNo;
    private LocalDateTime createTime;
}