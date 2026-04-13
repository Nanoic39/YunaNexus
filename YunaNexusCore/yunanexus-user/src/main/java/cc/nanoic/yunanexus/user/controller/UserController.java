package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.user.common.Result;
import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import cc.nanoic.yunanexus.user.entity.VO.PingVO;
import cc.nanoic.yunanexus.user.service.PingService;
import cc.nanoic.yunanexus.user.service.UsersService;
import cc.nanoic.yunanexus.user.utils.FormatTime;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/")
public class UserController {

    @Resource
    UsersService usersService;

    @Resource
    PingService pingService;

    @Resource
    FormatTime formatTime;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * PING!
     * @return PingVO
     */
    @GetMapping("/ping")
    public Result<PingVO> ping() {

        PingVO pingVO = new PingVO();
        ServiceVersion serviceVersion = pingService.getCurrentVersion();

        // 服务启动时间
        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime startDateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime),
                java.time.ZoneId.systemDefault()
        );

        // 运行时长（毫秒）
        long runTimeMillis = System.currentTimeMillis() - startTime;

        pingVO.setServerName(serviceName);
        pingVO.setServiceInfo(serviceVersion);
        pingVO.setCurrentTime(System.currentTimeMillis());
        pingVO.setStartTime(startDateTime);
        pingVO.setRunTimeMillis(runTimeMillis);
        pingVO.setRunTimeDesc(formatTime.formatMillis2String(runTimeMillis));

        return Result.success(pingVO);
    }
}
