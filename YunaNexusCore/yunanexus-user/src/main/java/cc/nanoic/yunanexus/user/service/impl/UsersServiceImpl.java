package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.DTO.UpdateUserInfoDTO;
import cc.nanoic.yunanexus.user.entity.UserInfo;
import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.client.AuthInternalClient;
import cc.nanoic.yunanexus.user.mapper.UserInfoMapper;
import cc.nanoic.yunanexus.user.mapper.UsersMapper;
import cc.nanoic.yunanexus.user.service.UsersService;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {
    @Resource
    YunaRedisService yunaRedisService;

    @Resource
    UserInfoMapper userInfoMapper;

    @Resource
    private AuthInternalClient authInternalClient;

    String sendLimitKeyPrefix = "email:verify:send:limit:";
    String checkLimitKeyPrefix = "email:verify:check:limit:";
    String verifyCodeKeyPrefix = "email:verify:code:";

    @Override
    public String generateEmailVerifyCode() {
        // 随机六位数字
        return RandomUtil.randomNumbers(6);
    }

    // 限流相关
    @Override
    public boolean isSendLimited(String email) {
        return !yunaRedisService.allowRequest(sendLimitKeyPrefix + email, 1, Duration.ofSeconds(60));
    }

    // 限流相关
    @Override
    public boolean isCheckLimited(String email) {
        return !yunaRedisService.allowRequest(checkLimitKeyPrefix + email, 5, Duration.ofSeconds(60));
    }

    @Override
    public void cacheVerifyCode(String email, String verifyCode, Duration expireTime) {
        // 生成验证码缓存键
        yunaRedisService.set(verifyCodeKeyPrefix + email, verifyCode, expireTime);

        // 增加限流键
        yunaRedisService.set(sendLimitKeyPrefix + email, verifyCode, expireTime);
    }

    @Override
    public boolean verifyEmailCode(String email, String verifyCode) {
        // 获取验证码
        String code = yunaRedisService.get(verifyCodeKeyPrefix + email);
        return String.valueOf(verifyCode).equals(code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO register) {
        // 插入数据到User中
        Users user = new Users();

        user.setUuid(UUID.randomUUID().toString());
        user.setUsername(register.getUsername());
        user.setPassword(BCrypt.hashpw(register.getPassword()));
        user.setEmail(register.getEmail());
        user.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);

        this.save(user);

        Long userId = user.getId();
        if (userId != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Map<String, Object> req = new HashMap<>();
                    req.put("userId", userId);
                    req.put("roleName", "USER");
                    authInternalClient.bindRole(req);
                }
            });
        }

        // 插入数据到userinfo中
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setNickname(register.getNickname());
        userInfo.setGender(register.getGender());
        userInfo.setUpdateTime(now);
        userInfoMapper.insert(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentUserInfo(Long userId, UpdateUserInfoDTO updateUserInfoDTO) {
        if (userId == null || updateUserInfoDTO == null) {
            throw new BusinessException(R.PARAM_ERROR, "请求参数不能为空");
        }

        String nickname = normalize(updateUserInfoDTO.getNickname());
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException(R.PARAM_ERROR, "昵称不能为空");
        }
        if (nickname.length() > 20) {
            throw new BusinessException(R.PARAM_ERROR, "昵称长度不能超过20个字符");
        }

        String gender = normalize(updateUserInfoDTO.getGender());
        if (!StringUtils.hasText(gender)) {
            gender = "未知";
        }
        if (gender.length() > 10) {
            throw new BusinessException(R.PARAM_ERROR, "性别长度不能超过10个字符");
        }

        LocalDate birthday = updateUserInfoDTO.getBirthday();
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            throw new BusinessException(R.PARAM_ERROR, "生日不能晚于今天");
        }

        UserInfo userInfo = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUserId, userId)
                        .last("LIMIT 1")
        );

        LocalDateTime now = LocalDateTime.now();
        if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.setUserId(userId);
            userInfo.setNickname(nickname);
            userInfo.setGender(gender);
            userInfo.setBirthday(birthday);
            userInfo.setUpdateTime(now);
            userInfoMapper.insert(userInfo);
            return;
        }

        userInfo.setNickname(nickname);
        userInfo.setGender(gender);
        userInfo.setBirthday(birthday);
        userInfo.setUpdateTime(now);
        userInfoMapper.updateById(userInfo);
    }

    @Override
    public boolean isExistsUser(String username) {
        return this.getOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, username)) != null;
    }

    @Override
    public boolean isExistsEmail(String email) {
        return this.getOne(new LambdaQueryWrapper<Users>().eq(Users::getEmail, email)) != null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
