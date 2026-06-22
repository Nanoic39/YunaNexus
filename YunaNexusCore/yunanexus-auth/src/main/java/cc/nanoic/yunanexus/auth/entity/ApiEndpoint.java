package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_endpoints")
public class ApiEndpoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceName;
    private String httpMethod;
    private String pathPattern;
    private String requiredCode;
    private String description;
    private Integer source; // 0自动上报 1管理端配置
    private Integer status; // 0停用 1启用
    private LocalDateTime reportedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
