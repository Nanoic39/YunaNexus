package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.user.entity.DTO.RegisterDTO;
import cc.nanoic.yunanexus.user.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;


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
    void cacheVerifyCode(String email, String verifyCode);

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
     * @return 注册结果
     */
    void register(RegisterDTO register);
}
