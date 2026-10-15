package cc.nanoic.yunanexus.meta.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("id_segment")
public class IdSegment {
    @TableId
    private String bizTag;
    private Long maxId;
    private Integer step;
    private Integer version;
    private LocalDateTime updateTime;
    private String description;
}
