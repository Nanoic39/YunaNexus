package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.UserInfo;
import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.mapper.UserInfoMapper;
import cc.nanoic.yunanexus.user.mapper.UsersMapper;
import cc.nanoic.yunanexus.user.service.UsersService;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {
    @Resource
    YunaRedisService yunaRedisService;

    @Resource
    UserInfoMapper userInfoMapper;

    String sendLimitKeyPrefix = "email:verify:send:limit:";
    String checkLimitKeyPrefix = "email:verify:check:limit:";
    String verifyCodeKeyPrefix = "email:verify:code:";

    @Override
    public String generateEmailVerifyCode() {
        // 随机六位数字
        Random random = new Random();
        int randomCode = random.nextInt(900000) + 100000;
        return String.valueOf(randomCode);
    }

    @Override
    public boolean isSendLimited(String email) {
        return !yunaRedisService.allowRequest(sendLimitKeyPrefix + email, 1, Duration.ofSeconds(60));
    }

    @Override
    public boolean isCheckLimited(String email) {
        return !yunaRedisService.allowRequest(checkLimitKeyPrefix + email, 5, Duration.ofSeconds(60));
    }

    @Override
    public void cacheVerifyCode(String email, String verifyCode) {
        // 生成验证码缓存键
        yunaRedisService.set(verifyCodeKeyPrefix + email, verifyCode, Duration.ofMinutes(10));

        // 增加限流键
        yunaRedisService.set(sendLimitKeyPrefix + email, verifyCode, Duration.ofMinutes(10));
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

        // 插入数据到userinfo中
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setNickname(register.getNickname());
        userInfo.setUpdateTime(now);
        userInfoMapper.insert(userInfo);
    }
}
