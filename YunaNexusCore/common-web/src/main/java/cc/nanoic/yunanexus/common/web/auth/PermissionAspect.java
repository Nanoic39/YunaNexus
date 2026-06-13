package cc.nanoic.yunanexus.common.web.auth;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

import java.util.Set;

@Aspect
public class PermissionAspect {

    @Before("@within(requirePermission) || @annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        if (!PermissionContext.hasIdentity()) {
            throw new BusinessException(R.NOT_LOGIN, "请先登录");
        }
        Set<String> perms = PermissionContext.getPermissions();
        if (perms.contains("*:*:*:*")) {
            return;
        }

        String[] anyOf = requirePermission.value().length > 0
                ? requirePermission.value() : requirePermission.anyOf();
        if (anyOf.length > 0) {
            for (String p : anyOf) {
                if (perms.contains(p)) return;
            }
            throw new BusinessException(R.NOT_PERMISSION, "缺少所需权限");
        }

        String[] allOf = requirePermission.allOf();
        for (String p : allOf) {
            if (!perms.contains(p)) {
                throw new BusinessException(R.NOT_PERMISSION, "缺少所需权限: " + p);
            }
        }
    }
}