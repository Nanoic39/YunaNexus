package cc.nanoic.yunanexus.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceVersion {
    /**
     * 服务版本配置表主键
     */
    private Long id;

    /**
     * 服务标识(服务名称)
     */
    private String serviceKey;

    /**
     * 最新版本号 例：1.0.0
     */
    private String latestVersion;

    /**
     * 更新说明
     */
    private String updateDesc;

    /**
     * 更新下载地址
     */
    private String updateUrl;

    /**
     * 是否强制更新：0不强制 1强制
     */
    private Integer forceUpdate;

    /**
     * 启用状态：0禁用(最新版本出现异常需要紧急回滚版本时) 1启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
