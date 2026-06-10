package cc.nanoic.yunanexus.common.web.auth;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;

import java.util.Set;

public class PermissionUtil {

    public static boolean hasRole(String role) {
        return PermissionContext.getRoles().contains(role);
    }

    public static boolean hasAnyRole(String... roles) {
        Set<String> myRoles = PermissionContext.getRoles();
        for (String r : roles) {
            if (myRoles.contains(r)) return true;
        }
        return false;
    }

    public static boolean hasPermission(String permission) {
        Set<String> perms = PermissionContext.getPermissions();
        if (perms.contains("*:*:*:*")) return true;
        return perms.contains(permission);
    }

    public static boolean hasAnyPermission(String... permissions) {
        Set<String> perms = PermissionContext.getPermissions();
        if (perms.contains("*:*:*:*")) return true;
        for (String p : permissions) {
            if (perms.contains(p)) return true;
        }
        return false;
    }

    public static void checkPermission(String permission) {
        if (!hasPermission(permission)) {
            throw new BusinessException(R.NOT_PERMISSION, "缺少所需权限");
        }
    }

    public static void checkRole(String role) {
        if (!hasRole(role)) {
            throw new BusinessException(R.NOT_PERMISSION, "缺少所需角色");
        }
    }

    public static void checkLogin() {
        if (!PermissionContext.hasIdentity()) {
            throw new BusinessException(R.NOT_LOGIN, "请先登录");
        }
    }

}
