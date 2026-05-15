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
import cc.nanoic.yunanexus.user.entity.DTO.UpdateUserInfoDTO;
import cc.nanoic.yunanexus.user.entity.ServiceVersion;
import cc.nanoic.yunanexus.user.entity.UserInfo;
import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.entity.VO.PingVO;
import cc.nanoic.yunanexus.user.entity.VO.UserInfoVO;
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

    /**
     * OAuth验证账户
     * @param oAuthVerifyUserAccountDTO 账号密码
     * @return 账户信息
     * TODO: 全部使用实体，便于固定格式处理数据
     */
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
        Result<Users> currentUserResult = resolveCurrentUser(authorization);
        if (currentUserResult.getCode() != R.SUCCESS.getCode() || currentUserResult.getData() == null) {
            return currentUserResult;
        }

        Users user = currentUserResult.getData();
        Long userId = user.getId();

        UserInfo userInfo = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUserId, userId)
                        .last("LIMIT 1")
        );

        UserInfoVO userInfoVO = buildUserInfoVO(user, userInfo);
        return Result.success(userInfoVO);
    }

    /**
     * 编辑当前用户信息
     * @param authorization accessToken
     * @param updateUserInfoDTO 更新内容
     * @return 更新后的结果
     */
    @PutMapping("/me")
    public Result<?> updateCurrentUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody UpdateUserInfoDTO updateUserInfoDTO) {
        Result<Users> currentUserResult = resolveCurrentUser(authorization);
        if (currentUserResult.getCode() != R.SUCCESS.getCode() || currentUserResult.getData() == null) {
            return currentUserResult;
        }

        Users user = currentUserResult.getData();
        Long userId = user.getId();

        usersService.updateCurrentUserInfo(userId, updateUserInfoDTO);

        UserInfo userInfo = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUserId, userId)
                        .last("LIMIT 1")
        );

        UserInfoVO userInfoVO = buildUserInfoVO(user, userInfo);
        return Result.success(userInfoVO, "用户信息更新成功");
    }

    /**
     * 外部使用的查询其它(或自己)信息的接口
     * @param userUuid 待查询的用户uuid
     * @param authorization 个人Token，用于无Uuid回退时查询自己的信息
     * @return 查询结果
     */
    @GetMapping("/user-info")
    public Result<?> userInfo(@RequestParam(value = "userUuid", required = false) String userUuid, @RequestHeader(value = "Authorization", required = false) String authorization) {
        if(!StringUtils.hasText(userUuid)) {
            // 不传参数说明查询的是自己，此时必须存在Token，否则返回异常
            // 但是只要存在Uuid就优先查询Uuid
            // 直接复用自查逻辑
            return currentUser(authorization);
        }

        // 查询用户信息
        Users user = usersService.getOne(
                new LambdaQueryWrapper<Users>()
                        .eq(Users::getUuid, userUuid)
                        .last("LIMIT 1")
        );

        if (user == null) {
            return Result.fail(R.NOT_FOUND, "用户不存在");
        }

        // 查询UserInfo
        UserInfo userInfo = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUserId, user.getId())
                        .last("LIMIT 1")
        );

        // 构造UserInfoVO
        // 隐藏生日(生日不应该被其他用户查询)
        userInfo.setBirthday(null);
        UserInfoVO userInfoVO = buildUserInfoVO(user, userInfo);

        return Result.success(userInfoVO);
    }

    private Result<Users> resolveCurrentUser(String authorization) {
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

        return Result.success(user);
    }

    /**
     * 构造返回信息
     * @param user 需要构造的用户账户
     * @param userInfo 需要构造的用户信息
     * @return 构造结果
     */
    private UserInfoVO buildUserInfoVO(Users user, UserInfo userInfo) {
        UserInfoVO userInfoVO = new UserInfoVO();
        // 无论什么情况下都不应该返回UserId
        userInfoVO.setUuid(user.getUuid()); // Uuid
        userInfoVO.setNickname(userInfo.getNickname()); // 昵称
        userInfoVO.setAvatarUuid(userInfo.getAvatarUuid()); // 头像Uuid
        userInfoVO.setGender(userInfo.getGender()); // 性别
        userInfoVO.setBirthday(userInfo.getBirthday()); // 生日属于敏感信息（传入前需要处理）
        userInfoVO.setCreateTime(user.getCreateTime()); // 账户创建时间
        userInfoVO.setUpdateTime(userInfo.getUpdateTime()); // 用户信息更新时间

        return userInfoVO;
    }

    /**
     * 将Object解析为Long(uid)
     * @param value Object值
     * @return uid
     */
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
