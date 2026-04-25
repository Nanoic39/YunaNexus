package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.UserInfo;
import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.mapper.UserInfoMapper;
import cc.nanoic.yunanexus.user.mapper.UsersMapper;
import cc.nanoic.yunanexus.user.service.UsersService;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.Duration;
import java.time.LocalDateTime;
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
        return RandomUtil.randomNumbers(6);
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

        // 插入数据到userinfo中
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setNickname(register.getNickname());
        userInfo.setGender(register.getGender());
        userInfo.setUpdateTime(now);
        userInfoMapper.insert(userInfo);
    }

    @Override
    public boolean isExistsUser(String username) {
        return this.getOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, username)) != null;
    }

    @Override
    public boolean isExistsEmail(String email) {
        return this.getOne(new LambdaQueryWrapper<Users>().eq(Users::getEmail, email)) != null;
    }
}
