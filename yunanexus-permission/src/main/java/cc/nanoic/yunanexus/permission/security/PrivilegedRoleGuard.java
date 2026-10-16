package cc.nanoic.yunanexus.permission.security;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

@Component
public class PrivilegedRoleGuard {
    private final AdminBearerVerifier verifier;
    private final PermRoleMapper roles;
    private final PermUserRoleMapper bindings;

    public PrivilegedRoleGuard(AdminBearerVerifier v, PermRoleMapper r, PermUserRoleMapper b) {
        verifier = v;
        roles = r;
        bindings = b;
    }

    private AdminBearerVerifier.Actor superActor() {
        var a = RequestContextHolder.getRequestAttributes();
        var actor =
                a instanceof ServletRequestAttributes s
                        ? verifier.actor(s.getRequest().getHeader("Authorization"))
                        : null;
        if (actor == null || !actor.superAdmin())
            throw new BizException(ErrorCode.FORBIDDEN, "需要超级管理员权限");
        return actor;
    }

    public void assignment(long userId, List<Long> targets) {
        var old = new HashSet<>(bindings.selectRoleIdsByUserId(userId));
        var next = new HashSet<>(targets == null ? List.<Long>of() : targets);
        next.remove(null);
        var changed = new HashSet<>(old);
        changed.addAll(next);
        changed.removeIf(id -> old.contains(id) == next.contains(id));
        for (long id : changed) {
            var role = roles.selectById(id);
            if (role != null && Set.of("super_admin", "admin").contains(role.getRoleCode())) {
                var actor = superActor();
                if (actor.userId() == userId
                        && old.contains(id)
                        && "super_admin".equals(role.getRoleCode()))
                    throw new BizException(ErrorCode.FORBIDDEN, "不能移除自己的超级管理员角色");
            }
        }
    }

    public void role(long id) {
        var role = roles.selectById(id);
        if (role != null && Set.of("super_admin", "admin", "user").contains(role.getRoleCode()))
            superActor();
    }
}
