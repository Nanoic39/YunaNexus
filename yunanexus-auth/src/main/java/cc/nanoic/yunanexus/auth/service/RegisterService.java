package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.RegisterRequest;

/**
 * 注册服务（ADR-021 最终一致补偿）。<p>
 *
 * 注册跨 meta（取号）/ auth（写 auth_user）/ user（写 sys_user，阶段 5）三服务。
 * 认证中心通过本地消息表记录「待写入 auth_user」的补偿，失败定时重试；用户中心
 * 提供幂等回查接口。注册采用 Redisson 分布式锁（ADR-016）防止并发重复创建。
 *
 * @author yunanexus
 */
public interface RegisterService {

    /**
     * 注册新账号。
     *
     * @param request 注册请求
     * @return 外部 ID（对外唯一标识）
     */
    String register(RegisterRequest request);
}
