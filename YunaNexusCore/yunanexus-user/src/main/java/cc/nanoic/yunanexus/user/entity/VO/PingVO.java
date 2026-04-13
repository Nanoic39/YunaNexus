package cc.nanoic.yunanexus.user.entity.VO;

import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;

@Data
public class PingVO {
    /**
     * 服务名称
     */
    @Value("${spring.application.name}")
    private String ServerName;

    /**
     * 服务信息
     */
    private ServiceVersion ServiceInfo;

    /**
     * 当前时间
     */
    private Long currentTime;

    /**
     * 开始时间戳
     */
    private LocalDateTime startTime;
    /**
     * 运行时长(毫秒)
     */
    private Long runTimeMillis;

    /**
     * 运行时长(解析后)
     */
    private String runTimeDesc;
}
