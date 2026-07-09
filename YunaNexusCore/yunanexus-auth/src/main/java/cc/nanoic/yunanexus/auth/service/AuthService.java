package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.entity.AdminInitKey;
import cc.nanoic.yunanexus.auth.entity.DTO.LoginRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.LoginResponse;
import cc.nanoic.yunanexus.auth.entity.DTO.RefreshRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.RegisterRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.RegisterResponse;
import cc.nanoic.yunanexus.auth.entity.Roles;
import cc.nanoic.yunanexus.auth.entity.UserIdentity;
import cc.nanoic.yunanexus.auth.entity.UserRoles;
import cc.nanoic.yunanexus.auth.mapper.AdminInitKeyMapper;
import cc.nanoic.yunanexus.auth.mapper.RolesMapper;
import cc.nanoic.yunanexus.auth.mapper.UserIdentityMapper;
import cc.nanoic.yunanexus.auth.mapper.UserRolesMapper;
import cc.nanoic.yunanexus.common.mail.service.MailService;
import cc.nanoic.yunanexus.common.mail.template.MailTemplateType;
import cc.nanoic.yunanexus.common.web.auth.JwtUtil;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.common.web.dto.UserCreateDTO;
import cc.nanoic.yunanexus.common.web.dto.UuidResult;
import cc.nanoic.yunanexus.common.web.util.UuidGenerator;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.digest.BCrypt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {
    @Resource
    private UserIdentityMapper userIdentityMapper;

    @Resource
    private AdminInitKeyMapper adminInitKeyMapper;

    @Resource
    private RolesMapper rolesMapper;

    @Resource
    private UserRolesMapper userRolesMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private AuthProperties authProperties;

    @Resource
    private MailService mailService;

    @Resource
    private UserRemoteService userRemoteService;

    @Resource
    private ResourceService resourceService;

    // 发送邮箱验证码
    public void sendEmailVerifyCode(String email) {
        // 校验邮箱格式
        if (!Validator.isEmail(email)) {
            throw new BusinessException(R.PARAM_ERROR, "邮箱格式不正确");
        }

        // 校验邮件发送cd
        RBucket<String> cdBucket = redissonClient.getBucket("email:cd:" + email);
        if (cdBucket.isExists()) {
            throw new BusinessException(R.PARAM_ERROR, "请求速度过快，请60秒后再试");
        }

        // 创建验证码并发送邮件
        String code = RandomUtil.randomNumbers(6);
        redissonClient.getBucket("email:code:" + email).set(code, Duration.ofMinutes(5));

        mailService.send(MailTemplateType.VERIFY_CODE, email, "YunaNexus 邮箱验证码",
                Map.of("code", code, "year", String.valueOf(Year.now().getValue())));

        redissonClient.getBucket("email:cd:" + email).set("1", Duration.ofSeconds(60));
    }

    // 注册
    public RegisterResponse register(RegisterRequest registerRequest) {
        UserIdentity userIdentityUserNameIsExist = userIdentityMapper.selectOne(
                new LambdaQueryWrapper<UserIdentity>()
                        .eq(UserIdentity::getUsername, registerRequest.getUsername())
                        .last("LIMIT 1"));
        UserIdentity userIdentityUserEmailIsExist = userIdentityMapper.selectOne(
                new LambdaQueryWrapper<UserIdentity>()
                        .eq(UserIdentity::getEmail, registerRequest.getEmail())
                        .last("LIMIT 1"));
        if (userIdentityUserNameIsExist != null) { // 存在用户认证信息
            throw new BusinessException(R.USER_EXIST, "用户名已存在");
        }
        if (userIdentityUserEmailIsExist != null) {
            throw new BusinessException(R.USER_EXIST, "邮箱已被注册");
        }
        // 校验邮箱验证码
        RBucket<String> codeBucket = redissonClient.getBucket("email:code:" + registerRequest.getEmail());
        String storedCode = codeBucket.get();
        if (storedCode == null || !storedCode.equals(registerRequest.getVerifyCode())) {
            throw new BusinessException(R.PARAM_ERROR, "验证码错误或已过期");
        }
        codeBucket.delete();

        String password = decryptPassword(registerRequest.getPassword());

        byte[] aesKey = loadAesKey();
        UuidResult uuidResult = UuidGenerator.generate(redissonClient, aesKey);
        byte[] globalId = uuidResult.getGlobalId();
        String internalUuid = uuidResult.getInternalUuid();
        String externalUuid = uuidResult.getExternalUuid();

        UserCreateDTO dto = new UserCreateDTO();
        dto.setUuid(internalUuid);
        dto.setGlobalId(HexUtil.encodeHexStr(globalId));
        dto.setNickname(registerRequest.getNickname());
        dto.setGender(registerRequest.getGender());

        try {
            Result<?> createResult = userRemoteService.createUser(dto);
            if (createResult.getCode() != R.SUCCESS.getCode()) {
                throw new BusinessException(R.SERVER_ERROR, "创建用户失败: " + createResult.getMsg());
            }

            UserIdentity identity = new UserIdentity();
            identity.setGlobalId(globalId);
            identity.setUsername(registerRequest.getUsername());
            identity.setPassword(BCrypt.hashpw(password));
            identity.setEmail(registerRequest.getEmail());
            identity.setStatus(1);
            identity.setCreatedAt(LocalDateTime.now());
            userIdentityMapper.insert(identity);

            bindRole(globalId, "USER");

            String adminInitKey = registerRequest.getAdminInitKey();
            if (adminInitKey != null && !adminInitKey.isEmpty()) {
                AdminInitKey key = adminInitKeyMapper.selectOne(
                        new LambdaQueryWrapper<AdminInitKey>()
                                .eq(AdminInitKey::getInitKey, adminInitKey)
                                .isNull(AdminInitKey::getUsedBy)
                                .last("LIMIT 1"));
                if (key != null) {
                    key.setUsedBy(globalId);
                    key.setUsedAt(LocalDateTime.now());
                    adminInitKeyMapper.updateById(key);
                    bindRole(globalId, "SUPER_ADMIN");
                } else {
                    throw new BusinessException(R.PARAM_ERROR, "管理员初始化密钥无效或已被使用");
                }
            }
        } catch (Exception e) {
            userRemoteService.cancelUser(globalId);
            throw new BusinessException(R.SERVER_ERROR, "注册失败！");
        }

        RegisterResponse resp = new RegisterResponse();
        resp.setUuid(externalUuid);
        return resp;
    }

    // 解密密码
    private String decryptPassword(String encryptedPassword) {
        String keyPath = authProperties.getRsa().getKeyPath();
        File privateFile = new File(keyPath, "id_rsa");
        if (!privateFile.exists()) {
            throw new BusinessException(R.SERVER_ERROR, "RSA密钥未初始化");
        }
        String privateKey = FileUtil.readUtf8String(privateFile);
        RSA rsa = new RSA(privateKey, null);
        return rsa.decryptStr(encryptedPassword, KeyType.PrivateKey);
    }

    // 加载AESKey
    private byte[] loadAesKey() {
        String keyPath = authProperties.getAes().getKeyPath();
        File keyFile = new File(keyPath, "aes.key");
        if (!keyFile.exists()) {
            throw new BusinessException(R.SERVER_ERROR, "AES密钥未初始化");
        }
        return FileUtil.readBytes(keyFile);
    }

    // 绑定角色
    private void bindRole(byte[] globalId, String roleName) {
        Roles role = rolesMapper.selectOne(
                new LambdaQueryWrapper<Roles>()
                        .eq(Roles::getName, roleName)
                        .last("LIMIT 1"));
        if (role == null) {
            return;
        }
        UserRoles userRole = new UserRoles();
        userRole.setGlobalId(globalId);
        userRole.setRoleId(role.getId());
        userRole.setStatus(1);
        userRolesMapper.insert(userRole);
    }

    // 登录
    public LoginResponse login(LoginRequest req) {
        UserIdentity identity = userIdentityMapper.selectOne(
                new LambdaQueryWrapper<UserIdentity>()
                        .eq(UserIdentity::getUsername, req.getUsername())
                        .last("LIMIT 1"));
        if (identity == null) {
            throw new BusinessException(R.NOT_LOGIN, "账号不存在");
        }
        if (identity.getStatus() != 1) {
            throw new BusinessException(R.NOT_LOGIN, "账号已被禁用或注销");
        }

        String password = decryptPassword(req.getPassword());
        if (!BCrypt.checkpw(password, identity.getPassword())) {
            throw new BusinessException(R.NOT_LOGIN, "密码错误");
        }

        byte[] globalId = identity.getGlobalId();
        Result<String> uuidResult = userRemoteService.getUuid(globalId);
        if (uuidResult == null || uuidResult.getCode() != R.SUCCESS.getCode() || uuidResult.getData() == null) {
            String detail = uuidResult == null ? "result is null"
                    : "code=" + uuidResult.getCode() + ", msg=" + uuidResult.getMsg();
            throw new BusinessException(R.SERVER_ERROR, "获取用户UUID失败: " + detail);
        }
        String internalUuid = uuidResult.getData();

        byte[] aesKey = loadAesKey();
        String externalUuid = UuidGenerator.internalToExternal(internalUuid, aesKey);

        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        assemblePermissions(globalId, roles, permissions);

        AuthProperties.Jwt jwtConfig = authProperties.getJwt();
        byte[] jwtSecret = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return buildTokenResponse(externalUuid, globalId, roles, permissions, jwtConfig, jwtSecret);
    }

    public LoginResponse refresh(RefreshRequest req) {
        RBucket<String> bucket = redissonClient.getBucket("refresh:" + req.getRefreshToken());
        String data = bucket.get();
        if (data == null) {
            throw new BusinessException(R.NOT_LOGIN, "refresh token 无效或已过期");
        }
        bucket.delete();

        JSONObject obj = JSON.parseObject(data);
        String externalUuid = obj.getString("uuid");
        byte[] globalId = HexUtil.decodeHex(obj.getString("globalId"));

        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        assemblePermissions(globalId, roles, permissions);

        AuthProperties.Jwt jwtConfig = authProperties.getJwt();
        byte[] jwtSecret = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        LoginResponse resp = buildTokenResponse(externalUuid, globalId, roles, permissions, jwtConfig, jwtSecret);
        return resp;
    }

    private void assemblePermissions(byte[] globalId, Set<String> roles, Set<String> permissions) {
        List<UserRoles> userRoles = userRolesMapper.selectList(
                new LambdaQueryWrapper<UserRoles>()
                        .eq(UserRoles::getGlobalId, globalId)
                        .eq(UserRoles::getStatus, 1));
        if (userRoles.isEmpty()) {
            roles.add("USER");
            return;
        }
        List<Long> roleIds = userRoles.stream().map(UserRoles::getRoleId).collect(Collectors.toList());
        List<Roles> roleList = rolesMapper.selectList(
                new LambdaQueryWrapper<Roles>()
                        .in(Roles::getId, roleIds));
        for (Roles r : roleList) {
            roles.add(r.getName());
            if (r.getPermissions() != null && !r.getPermissions().isEmpty()) {
                permissions.addAll(JSON.parseArray(r.getPermissions(), String.class));
            }
        }
    }

    public String getExternalUuid(byte[] globalId) {
        Result<String> uuidResult = userRemoteService.getUuid(globalId);
        if (uuidResult == null || uuidResult.getCode() != R.SUCCESS.getCode() || uuidResult.getData() == null) {
            String detail = uuidResult == null ? "result is null" : "code=" + uuidResult.getCode();
            throw new BusinessException(R.SERVER_ERROR, "获取用户UUID失败: " + detail);
        }
        String internalUuid = uuidResult.getData();
        byte[] aesKey = loadAesKey();
        return UuidGenerator.internalToExternal(internalUuid, aesKey);
    }

    public LoginResponse issueTokens(byte[] globalId) {
        String externalUuid = getExternalUuid(globalId);
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        assemblePermissions(globalId, roles, permissions);
        AuthProperties.Jwt jwtConfig = authProperties.getJwt();
        byte[] jwtSecret = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return buildTokenResponse(externalUuid, globalId, roles, permissions, jwtConfig, jwtSecret);
    }

    private LoginResponse buildTokenResponse(String externalUuid, byte[] globalId,
            Set<String> roles, Set<String> permissions,
            AuthProperties.Jwt jwtConfig, byte[] jwtSecret) {
        long expireMs = jwtConfig.getAccessExp() * 1000;
        String token = JwtUtil.createToken(externalUuid, globalId, roles, permissions, jwtSecret, expireMs);
        String refreshToken = RandomUtil.randomString(32);
        JSONObject refreshData = new JSONObject();
        refreshData.put("uuid", externalUuid);
        refreshData.put("globalId", HexUtil.encodeHexStr(globalId));
        redissonClient.getBucket("refresh:" + refreshToken)
                .set(refreshData.toJSONString(), Duration.ofSeconds(jwtConfig.getRefreshExp()));
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(token);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresIn(jwtConfig.getAccessExp());
        resp.setUuid(externalUuid);
        resp.setMenus(resourceService.buildMenuTree(permissions));
        resp.setButtons(resourceService.getUserButtonCodes(permissions));
        return resp;
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            redissonClient.getBucket("refresh:" + refreshToken).delete();
        }
    }

    public void logoutAll() {
        byte[] globalId = PermissionContext.getGlobalId();
        if (globalId == null) return;
        String prefix = "refresh:";
        // 遍历所有 refresh token，删除属于当前用户的
        var keys = redissonClient.getKeys();
        for (String key : keys.getKeysByPattern("refresh:*", 1000)) {
            RBucket<String> bucket = redissonClient.getBucket(key);
            String data = bucket.get();
            if (data != null && data.contains(HexUtil.encodeHexStr(globalId))) {
                bucket.delete();
            }
        }
    }
}
