package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.domain.AppealView;
import cc.nanoic.yunanexus.auth.domain.UserView;
import cc.nanoic.yunanexus.auth.entity.AuthUnlockAppeal;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthUnlockAppealMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.AdminService;
import cc.nanoic.yunanexus.auth.service.LoginAttemptService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证中心管理后台服务实现。
 *
 * <p>申诉审核采用 {@code WHERE status = 0} 的条件更新保证幂等；通过申诉会一并解锁关联账号。 用户管理复用登录流程的状态字段与失败计数语义：启用 /
 * 解锁均置正常并清零失败计数， 禁用后因令牌校验实时读取账号状态而立即失效，踢下线则撤销纪元并清理会话。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_AUDITOR = "admin";

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    private final AuthUnlockAppealMapper appealMapper;
    private final AuthUserMapper authUserMapper;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final AccountEpochService accountEpochService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cc.nanoic.yunanexus.auth.client.RootAdminClient rootRoles;

    public AdminServiceImpl(
            AuthUnlockAppealMapper appealMapper,
            AuthUserMapper authUserMapper,
            LoginAttemptService loginAttemptService,
            SessionService sessionService,
            AccountEpochService accountEpochService,
            Clock clock) {
        this.appealMapper = appealMapper;
        this.authUserMapper = authUserMapper;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
        this.accountEpochService = accountEpochService;
        this.clock = clock;
    }

    @Override
    public PageResult<AppealView> listAppeals(Integer status, int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        long total = appealMapper.countByStatus(status);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<AppealView> records =
                appealMapper.selectPage(status, offset(p, s), s).stream()
                        .map(AppealView::from)
                        .toList();
        return PageResult.of(records, total, p, s);
    }

    @Override
    public AppealView getAppeal(long id) {
        AuthUnlockAppeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BizException(ErrorCode.APPEAL_NOT_FOUND);
        }
        return AppealView.from(appeal);
    }

    @Override
    public void approveAppeal(long id, String auditor, String remark) {
        AuthUnlockAppeal appeal = requirePendingAppeal(id);
        int updated = appealMapper.audit(id, STATUS_APPROVED, auditorOf(auditor), remark, now());
        if (updated != 1) {
            throw new BizException(ErrorCode.APPEAL_ALREADY_AUDITED);
        }
        unlockAssociatedUser(appeal);
        log.info(
                "管理后台通过申诉 appealId={} userId={} auditor={}",
                id,
                appeal.getUserId(),
                auditorOf(auditor));
    }

    @Override
    public void rejectAppeal(long id, String auditor, String remark) {
        requirePendingAppeal(id);
        int updated = appealMapper.audit(id, STATUS_REJECTED, auditorOf(auditor), remark, now());
        if (updated != 1) {
            throw new BizException(ErrorCode.APPEAL_ALREADY_AUDITED);
        }
        log.info("管理后台驳回申诉 appealId={} auditor={}", id, auditorOf(auditor));
    }

    @Override
    public PageResult<UserView> listUsers(String keyword, Integer status, int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        long total = authUserMapper.countByCondition(kw, status);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<UserView> records =
                authUserMapper.selectPage(kw, status, offset(p, s), s).stream()
                        .map(UserView::from)
                        .toList();
        return PageResult.of(records, total, p, s);
    }

    @Override
    public PageResult<UserView> listUsers(
            String keyword, Integer status, int page, int size, Long roleId, Long permissionId) {
        if (roleId == null && permissionId == null) return listUsers(keyword, status, page, size);
        if ((roleId != null && roleId <= 0)
                || (permissionId != null && permissionId <= 0)
                || page > 10000) throw new BizException(ErrorCode.BAD_REQUEST, "筛选参数无效");
        if (rootRoles == null) throw new BizException(ErrorCode.INTERNAL_ERROR, "权限筛选暂不可用");
        var result = rootRoles.filterUsers(roleId, permissionId);
        if (result == null || result.getCode() != 200 || result.getData() == null)
            throw new BizException(ErrorCode.INTERNAL_ERROR, "权限筛选暂不可用");
        var ids = result.getData();
        int p = normalizePage(page), s = normalizeSize(size);
        if (ids.size() > 10000) throw new BizException(ErrorCode.BAD_REQUEST, "匹配用户过多，请缩小角色或权限范围");
        if (ids.isEmpty()) return PageResult.empty(p, s);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = authUserMapper.countFiltered(kw, status, ids);
        if (total == 0) return PageResult.empty(p, s);
        return PageResult.of(
                authUserMapper.selectFiltered(kw, status, ids, offset(p, s), s).stream()
                        .map(UserView::from)
                        .toList(),
                total,
                p,
                s);
    }

    @Override
    public UserView getUser(long userId) {
        return UserView.from(requireUser(userId));
    }

    @Override
    public void enableUser(long userId) {
        resetToNormal(userId, "启用");
    }

    @Override
    public void disableUser(long userId) {
        var auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (auth != null
                && auth.getPrincipal()
                        instanceof cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity i
                && i.userId() == userId)
            throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                    cc.nanoic.yunanexus.common.web.constant.ErrorCode.FORBIDDEN, "不能禁用自己的账户");
        if (rootRoles != null
                && auth != null
                && auth.getPrincipal()
                        instanceof cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity i) {
            var target = rootRoles.superCheck(userId);
            var actor = rootRoles.superCheck(i.userId());
            if (target == null
                    || actor == null
                    || target.getCode() != 200
                    || actor.getCode() != 200)
                throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                        cc.nanoic.yunanexus.common.web.constant.ErrorCode.INTERNAL_ERROR,
                        "权限校验不可用");
            if (Boolean.TRUE.equals(target.getData()) && !Boolean.TRUE.equals(actor.getData()))
                throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                        cc.nanoic.yunanexus.common.web.constant.ErrorCode.FORBIDDEN, "不能禁用超级管理员");
        }
        requireUser(userId);
        authUserMapper.updateStatusAndLock(userId, 0, null, null);
        log.info("管理后台禁用账号 userId={}", userId);
    }

    @Override
    public void unlockUser(long userId) {
        resetToNormal(userId, "解锁");
    }

    @Override
    public void kickUser(long userId) {
        requireUser(userId);
        requireKickAllowed(userId);
        int kicked = sessionService.kickAll(userId);
        accountEpochService.revoke(userId);
        log.info("管理后台踢出账号会话 userId={} 会话数={}", userId, kicked);
    }

    /** No emergency credential or privileged actor may evict a super administrator. */
    private void requireKickAllowed(long userId) {
        if (rootRoles == null) throw new BizException(ErrorCode.INTERNAL_ERROR, "权限校验不可用");
        if (roleResult(rootRoles.superCheck(userId)))
            throw new BizException(ErrorCode.FORBIDDEN, "禁止踢出超级管理员");
        var auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new BizException(ErrorCode.FORBIDDEN, "缺少管理员身份");
        if (auth.getPrincipal()
                instanceof cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity actor) {
            if (!roleResult(rootRoles.check(actor.userId())))
                throw new BizException(ErrorCode.FORBIDDEN, "无管理权限");
            boolean actorSuper = roleResult(rootRoles.superCheck(actor.userId()));
            if (!actorSuper && (actor.userId() == userId || roleResult(rootRoles.check(userId))))
                throw new BizException(ErrorCode.FORBIDDEN, "不能踢出自己或同级管理员");
        } else if (!"admin".equals(auth.getPrincipal())
                || auth.getAuthorities().stream()
                        .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无管理权限");
        }
    }

    private boolean roleResult(cc.nanoic.yunanexus.common.core.model.Result<Boolean> result) {
        if (result == null || result.getCode() != 200 || result.getData() == null)
            throw new BizException(ErrorCode.INTERNAL_ERROR, "权限校验不可用");
        return result.getData();
    }

    private void resetToNormal(long userId, String action) {
        AuthUser user = requireUser(userId);
        authUserMapper.updateStatusAndLock(userId, 1, null, null);
        loginAttemptService.reset(user.getUsername());
        log.info("管理后台{}账号 userId={}", action, userId);
    }

    private AuthUnlockAppeal requirePendingAppeal(long id) {
        AuthUnlockAppeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BizException(ErrorCode.APPEAL_NOT_FOUND);
        }
        if (appeal.getStatus() == null || appeal.getStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.APPEAL_ALREADY_AUDITED);
        }
        return appeal;
    }

    private AuthUser requireUser(long userId) {
        AuthUser user = authUserMapper.selectByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ADMIN_USER_NOT_FOUND);
        }
        return user;
    }

    private void unlockAssociatedUser(AuthUnlockAppeal appeal) {
        Long userId = appeal.getUserId();
        if (userId == null) {
            return;
        }
        AuthUser user = authUserMapper.selectByUserId(userId);
        if (user == null) {
            return;
        }
        authUserMapper.updateStatusAndLock(userId, 1, null, null);
        loginAttemptService.reset(user.getUsername());
    }

    private String auditorOf(String auditor) {
        return (auditor == null || auditor.isBlank()) ? DEFAULT_AUDITOR : auditor.trim();
    }

    private int normalizePage(int page) {
        if (page > 10000) throw new BizException(ErrorCode.BAD_REQUEST, "页码超出查询上限");
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int offset(int page, int size) {
        return (page - 1) * size;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
