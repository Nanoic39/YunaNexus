package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.domain.UnlockAppealRequest;
import cc.nanoic.yunanexus.auth.entity.AuthUnlockAppeal;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthUnlockAppealMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.service.UnlockAppealService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 解锁申诉服务实现。<p>
 *
 * 申诉需验证密码以证明账号归属；同一账号存在待处理申诉时拒绝重复提交。
 * 申诉提交后进入待处理（status=0）队列，由管理后台审核（阶段 3.9 后）。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class UnlockAppealServiceImpl implements UnlockAppealService {

    private final AuthUserMapper authUserMapper;
    private final AuthUnlockAppealMapper appealMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UnlockAppealServiceImpl(AuthUserMapper authUserMapper,
                                   AuthUnlockAppealMapper appealMapper,
                                   PasswordEncoder passwordEncoder,
                                   Clock clock) {
        this.authUserMapper = authUserMapper;
        this.appealMapper = appealMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public long submit(UnlockAppealRequest request) {
        String username = request.getUsername().trim();
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            // 账号不存在：不暴露「账号是否存在」，统一返回凭证错误
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        // 验证密码证明归属
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        // 防重复提交：存在待处理申诉则拒绝
        if (appealMapper.countPendingByUsername(username) > 0) {
            throw new BizException(ErrorCode.APPEAL_ALREADY_PENDING);
        }

        AuthUnlockAppeal appeal = new AuthUnlockAppeal();
        appeal.setUserId(user.getUserId());
        appeal.setUsername(username);
        appeal.setAppealReason(request.getAppealReason());
        appeal.setContact(request.getContact());
        appeal.setStatus(0);
        appeal.setCreateTime(now());
        appeal.setUpdateTime(now());
        int inserted = appealMapper.insert(appeal);
        if (inserted != 1) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        log.info("解锁申诉已提交 userId={}", user.getUserId());
        return appeal.getId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
