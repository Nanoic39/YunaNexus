package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.mail.enums.MailTemplateType;
import cc.nanoic.yunanexus.common.mail.service.YunaMailService;
import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.common.security.annotation.RSADecryptRequest;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.user.client.AuthInternalClient;
import cc.nanoic.yunanexus.user.entity.DTO.EmailVerifySend;
import cc.nanoic.yunanexus.user.entity.DTO.OAuthVerifyUserAccountDTO;
import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import cc.nanoic.yunanexus.user.entity.UserInfo;
import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.entity.VO.PingVO;
import cc.nanoic.yunanexus.user.mapper.UserInfoMapper;
import cc.nanoic.yunanexus.user.service.PingService;
import cc.nanoic.yunanexus.user.service.UsersService;
import cc.nanoic.yunanexus.user.utils.FormatTime;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

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

    @Value("${yunanexus.mail.verify.code.expire-seconds}")
    private long expireSeconds;

    @Resource
    private AuthInternalClient authInternalClient;

    @Resource
    private UserInfoMapper userInfoMapper;

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

    // TODO: 考虑该怎么实现优雅的Sentinel限流
    // 我认为 校验是否存在该用户 和 校验是否存在邮箱 不适合再去使用Redis做限流
    // 敏感精细操作使用Redis精确限流，网关/接口层直接粗限流即可
    // (那样的话光一套注册流程要的Redis键就煲炸了)
    // 但是这里如果不设限的话就可以一直调用这个接口.
    /**
     * 账户注册功能
     * @param registerDTO 注册传递参数
     * @return 注册结果
     */
    @RSADecryptRequest
    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterDTO registerDTO) {
        String email = registerDTO.getEmail();
        String username = registerDTO.getUsername();

        // 注册时需要先校验是否存在该用户
        if (usersService.isExistsUser(username)) {
            throw new BusinessException(R.ACCOUNT_EXISTS);
        }
        // 校验是否存在邮箱
        if (usersService.isExistsEmail(email)) {
            throw new BusinessException(R.EMAIL_EXISTS);
        }

        // 是否被查询限流
        if (usersService.isCheckLimited(email)) {
            return Result.fail(R.REQ_API_LIMIT);
        }

        // 校验邮箱是否已验证
        if(!usersService.verifyEmailCode(registerDTO.getEmail(), registerDTO.getVerifyCode())) {
            return Result.fail(R.PARAM_ERROR, "邮箱验证码有误!");
        }

        // 进入注册流程
        usersService.register(registerDTO);

        // 如果没有报错
        return Result.success("注册成功!");
    }

    /**
     * 发送邮箱验证码
     * @param emailVerifySend 要发送验证邮件的邮箱
     * @return 返回结果
     */
    @PostMapping("/email-verify-send")
    public Result<?> emailVerifySend(@RequestBody EmailVerifySend emailVerifySend) {
        String email = emailVerifySend.getEmail();
        // 验证码过期时间
        Duration expireTime = Duration.ofSeconds(expireSeconds);

        // 校验是否被限流
        if(usersService.isSendLimited(email)) {
            return Result.fail(R.REQ_API_LIMIT, "请求过于频繁，请 60 秒后再试");
        }

        // 生成验证码
        String code = usersService.generateEmailVerifyCode();

        // 储存验证码并增加限流
        usersService.cacheVerifyCode(email, code, expireTime);

        // 发送验证码
        Map<String, Object> params = Map.of("code", code, "minutes", expireTime.toMinutes());
        yunaMailService.sendMail(email, MailTemplateType.VERIFICATION, params);

        return Result.success("验证码已发送到您的邮箱!");
    }

    @PostMapping("/oauth/verify")
    public Result<?> oAuthVerifyUserAccount(@RequestBody OAuthVerifyUserAccountDTO oAuthVerifyUserAccountDTO) {
        String username = oAuthVerifyUserAccountDTO.getUsername();
        String password = oAuthVerifyUserAccountDTO.getPassword();
        
        if(!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Result.fail(R.PARAM_ERROR, "用户名或密码不能为空!");
        }

        // 查询账户信息
        Users user = usersService.getOne(
            new LambdaQueryWrapper<Users>()
                    .eq(Users::getUsername, username)
                    .last("LIMIT 1")
        );

        // 校验密码
        if(user == null || !BCrypt.checkpw(password, user.getPassword())) {
            return Result.fail(R.ACCOUNT_ERROR, "账号或密码错误");
        }

        Integer userStatus = user.getStatus();
        if (userStatus == null || userStatus != 1) {
            // 增加一层判断是注销还是封禁
            return Integer.valueOf(2).equals(userStatus) ? Result.fail(R.ACCOUNT_DISABLED) : Result.fail(R.ACCOUNT_DELETE);
        }

        // 修改这里时需要同步修改 OAuthLoginController 中的获取内容
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("userUuid", user.getUuid());
        data.put("username", user.getUsername());

        return Result.success(data);
    }

    /**
     * 当前用户信息
     * @param authorization accessToken
     * @return 结果
     */
    @GetMapping("/me")
    public Result<?> currentUser(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return Result.fail(R.PARAM_ERROR, "Authorization格式错误");
        }

        Result<Map<String, Object>> parsedResp;
        try {
            parsedResp = authInternalClient.parseToken(authorization);
        } catch (Exception e) {
            return Result.fail(R.SERVER_ERROR, "认证服务暂时不可用");
        }

        if (parsedResp == null || parsedResp.getCode() != R.SUCCESS.getCode() || parsedResp.getData() == null) {
            return Result.fail(R.NOT_LOGIN, "token无效或已过期");
        }

        Long userId = parseLong(parsedResp.getData().get("userId"));
        if (userId == null) {
            return Result.fail(R.NOT_LOGIN, "token用户信息无效");
        }

        Users user = usersService.getById(userId);
        if (user == null) {
            return Result.fail(R.ACCOUNT_ERROR, "用户不存在");
        }

        UserInfo userInfo = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUserId, userId)
                        .last("LIMIT 1")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("userUuid", user.getUuid());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("status", user.getStatus());
        if (userInfo != null) {
            data.put("nickname", userInfo.getNickname());
            data.put("avatarUuid", userInfo.getAvatarUuid());
            data.put("gender", userInfo.getGender());
            data.put("birthday", userInfo.getBirthday());
        }

        return Result.success(data);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }


}
