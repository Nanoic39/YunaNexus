package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.mapper.UsersMapper;
import cc.nanoic.yunanexus.user.service.UsersService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.Random;

import org.springframework.stereotype.Service;

@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {
    @Override
    public String generateEmailVerifyCode() {
        // 随机六位数字
        Random random = new Random();
        int randomCode = random.nextInt(900000) + 100000;
        return String.valueOf(randomCode);
    }
}
