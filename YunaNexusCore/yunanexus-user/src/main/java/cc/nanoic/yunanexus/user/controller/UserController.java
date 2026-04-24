package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.mail.enums.MailTemplateType;
import cc.nanoic.yunanexus.common.mail.service.YunaMailService;
import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.common.security.annotation.RSADecryptRequest;
import cc.nanoic.yunanexus.user.common.R;
import cc.nanoic.yunanexus.user.common.Result;
import cc.nanoic.yunanexus.user.entity.DTO.EmailVerifySend;
import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import cc.nanoic.yunanexus.user.entity.VO.PingVO;
import cc.nanoic.yunanexus.user.service.PingService;
import cc.nanoic.yunanexus.user.service.UsersService;
import cc.nanoic.yunanexus.user.utils.FormatTime;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class UserController {

    @Resource
    UsersService usersService;

    @Resource
    PingService pingService;

    @Resource
    FormatTime formatTime;

    @Resource
    YunaMailService yunaMailService;

    @Resource
    YunaRedisService yunaRedisService;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * PING!
     * TODO: 接口待删除
     * @return PingVO
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
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

    @RSADecryptRequest
    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterDTO registerDTO) {
        // 校验邮箱是否已验证
        

        return Result.success("注册成功!");
    }

    @PostMapping("/email-verify-send")
    public Result<?> emailVerifySend(@RequestBody EmailVerifySend emailVerifySend) {
        String email = emailVerifySend.getEmail();

        // 增加限流键
        String limitKey = "email:verify:limit:" + email;
        if (!yunaRedisService.allowRequest(limitKey, 1, Duration.ofSeconds(60))) {
            return Result.fail(R.PARAM_ERROR, "请求过于频繁，请 60 秒后再试");
        }

        // 生成验证码
        String code = usersService.generateEmailVerifyCode();

        // 生成验证码缓存键
        String codeKey = "email:verify:code:" + email;
        yunaRedisService.set(codeKey, code, Duration.ofMinutes(10));

        // 发送验证码
        Map<String, Object> params = new HashMap<>();
        params.put("code", code);
        params.put("minutes", 10);
        yunaMailService.sendMail(email, MailTemplateType.VERIFICATION, params);

        return Result.success("验证码已发送到您的邮箱!");
    }
}
