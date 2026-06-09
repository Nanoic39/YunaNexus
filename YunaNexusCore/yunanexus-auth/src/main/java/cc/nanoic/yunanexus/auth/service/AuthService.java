package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.client.UserInternalClient;
import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.entity.AdminInitKey;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Map;

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
    private UserInternalClient userInternalClient;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private AuthProperties authProperties;

    @Resource
    private MailService mailService;

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
        redissonClient.getBucket("email:cd:" + email).set("1", Duration.ofSeconds(60));

        mailService.send(MailTemplateType.VERIFY_CODE, email, "YunaNexus 邮箱验证码", Map.of("code", code, "year", String.valueOf(Year.now().getValue())));
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

        userInternalClient.createUser(dto);

        try {
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
                }
            }
        } catch (Exception e) {
            userInternalClient.cancelUser(globalId);
            throw new BusinessException(R.SERVER_ERROR, "注册失败！");
        }

        RegisterResponse resp = new RegisterResponse();
        resp.setUuid(externalUuid);
        return resp;
    }

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

    private byte[] loadAesKey() {
        String keyPath = authProperties.getAes().getKeyPath();
        File keyFile = new File(keyPath, "aes.key");
        if (!keyFile.exists()) {
            throw new BusinessException(R.SERVER_ERROR, "AES密钥未初始化");
        }
        return FileUtil.readBytes(keyFile);
    }

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

}
