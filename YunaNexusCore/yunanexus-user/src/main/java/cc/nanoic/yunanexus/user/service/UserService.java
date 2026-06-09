package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.dto.UserCreateDTO;
import cc.nanoic.yunanexus.user.config.UserProperties;
import cc.nanoic.yunanexus.user.entity.UserEconomy;
import cc.nanoic.yunanexus.user.entity.UserProfile;
import cc.nanoic.yunanexus.user.entity.Users;
import cc.nanoic.yunanexus.user.entity.VO.UserProfileVO;
import cc.nanoic.yunanexus.user.mapper.UserEconomyMapper;
import cc.nanoic.yunanexus.user.mapper.UserProfileMapper;
import cc.nanoic.yunanexus.user.mapper.UsersMapper;
import cn.hutool.core.util.HexUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Resource
    private UsersMapper usersMapper;

    @Resource
    private UserProfileMapper userProfileMapper;

    @Resource
    private UserEconomyMapper userEconomyMapper;

    @Resource
    private UserProperties userProperties;

    // 根据userId获取UserProfile
    // 使用includeBirthday参数是否包含生日信息（可以被uuid查询和me查询分别调用）
    public UserProfileVO getUserProfile(byte[] globalId, boolean includeBirthday) {
        Users user = findUserByGlobalId(globalId);
        UserProfile userProfile = findUserProfileByGid(user.getGlobalId());
        UserEconomy userEconomy = findUserEconomyByGid(user.getGlobalId());
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(userProfile, vo);
        BeanUtils.copyProperties(userEconomy, vo);
        if (includeBirthday) {
            return vo;
        }
        vo.setBirthday(null); // 根据userId查询时不应该获取生日这种敏感信息
        return vo;
        
    }

    // 通过uuid查询User信息
    private Users findUserByUuid(String uuid) {
        Users user = usersMapper.selectOne(
                new LambdaQueryWrapper<Users>()
                        .eq(Users::getUuid, uuid)
                        .last("LIMIT 1")
        );
        if (user == null) {
            throw new BusinessException(R.USER_NOTFOUND, "用户不存在");
        }
        return user;
    }

    // 通过id查询User信息
    private Users findUserByGlobalId(byte[] globalId) {
        Users user = usersMapper.selectById(globalId);
        if (user == null) {
            throw new BusinessException(R.USER_NOTFOUND, "用户不存在");
        }
        return user;
    }

    // 根据gid查询UserProfile
    private UserProfile findUserProfileByGid(byte[] gid) {
        UserProfile userProfile = userProfileMapper.selectById(gid);
        if (userProfile == null) {
            throw new BusinessException(R.USER_NOTFOUND, "用户不存在");
        }
        return userProfile;
    }

    // 根据gid查询UserEconomy
    private UserEconomy findUserEconomyByGid(byte[] gid) {
        UserEconomy userEconomy = userEconomyMapper.selectById(gid);
        if (userEconomy == null) {
            throw new BusinessException(R.USER_NOTFOUND, "用户不存在");
        }
        return userEconomy;
    }

    // 取消注册
    public void cancelRegister(byte[] gid) {
        Users user = findUserByGlobalId(gid);
        user.setRouteVersion(-1);
        usersMapper.updateById(user);
    }

    // 创建用户
    @Transactional
    public void createUser(UserCreateDTO userCreateDTO) {
        byte[] globalId = HexUtil.decodeHex(userCreateDTO.getGlobalId());
        Users user = new Users();
        user.setUuid(userCreateDTO.getUuid());
        user.setGlobalId(globalId); // 设置GID
        user.setRouteVersion(userProperties.getVersion());
        usersMapper.insert(user);

        UserProfile userProfile = new UserProfile();
        userProfile.setGlobalId(globalId);
        userProfile.setNickname(userCreateDTO.getNickname() == null ? "YUNA❀清汐" : userCreateDTO.getNickname());
        userProfile.setGender(userCreateDTO.getGender() == null ? "未知" : userCreateDTO.getGender());
        userProfileMapper.insert(userProfile);

        UserEconomy economy = new UserEconomy();
        economy.setGlobalId(globalId);
        economy.setExp(0L);
        economy.setCoin(0L);
        userEconomyMapper.insert(economy);
    }

    
}