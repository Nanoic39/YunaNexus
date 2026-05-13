package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.DTO.UpdateUserInfoDTO;
import cc.nanoic.yunanexus.user.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.Duration;


public interface UsersService extends IService<Users> {

    /**
     * 生成验证码
     * @return 返回验证码值
     */
    String generateEmailVerifyCode();

    /**
     * 发送验证码是否被限流(true：是/false：否)
     * @param email 邮箱
     * @return 检测结果
     */
    boolean isSendLimited(String email);

    /**
     * 校验验证码是否被限流
     * @param email 邮箱
     * @return 检测结果
     */
    boolean isCheckLimited(String email);

    /**
     * 缓存验证码
     * @param email 邮箱
     * @param verifyCode 验证码
     */
    void cacheVerifyCode(String email, String verifyCode, Duration expireTime);

    /**
     * 校验验证码
     * @param email 邮箱
     * @param verifyCode 验证码
     * @return 校验结果
     */
    boolean verifyEmailCode(String email, String verifyCode);

    /**
     * 用户注册
     * @param register 注册DTO
     */
    void register(RegisterDTO register);

    /**
     * 校验是否存在用户
     * @param username 用户名
     * @return 校验结果
     */
    boolean isExistsUser(String username);

    /**
     * 校验是否存在邮箱
     * @param email 邮箱
     * @return 校验结果
     */
    boolean isExistsEmail(String email);

    /**
     * 更新当前用户资料
     * @param userId 用户ID
     * @param updateUserInfoDTO 更新内容
     */
    void updateCurrentUserInfo(Long userId, UpdateUserInfoDTO updateUserInfoDTO);
}
