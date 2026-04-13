package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.mapper.UsersMapper;
import cc.nanoic.yunanexus.user.service.UsersService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {
}
