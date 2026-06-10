package cc.nanoic.yunanexus.common.web.auth;

import java.util.Collections;
import java.util.Set;

public class PermissionContext {

    private static final ThreadLocal<String> UUID = new ThreadLocal<>();
    private static final ThreadLocal<byte[]> GLOBAL_ID = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> PERMISSIONS = new ThreadLocal<>();

    public static void setUuid(String uuid) { UUID.set(uuid); }
    public static String getUuid() { return UUID.get(); }

    public static void setGlobalId(byte[] globalId) { GLOBAL_ID.set(globalId); }
    public static byte[] getGlobalId() { return GLOBAL_ID.get(); }

    public static void setRoles(Set<String> roles) { ROLES.set(roles); }
    public static Set<String> getRoles() { return ROLES.get() != null ? ROLES.get() : Collections.emptySet(); }

    public static void setPermissions(Set<String> permissions) { PERMISSIONS.set(permissions); }
    public static Set<String> getPermissions() { return PERMISSIONS.get() != null ? PERMISSIONS.get() : Collections.emptySet(); }

    public static boolean hasIdentity() {
        return UUID.get() != null;
    }

    public static void clear() {
        UUID.remove();
        GLOBAL_ID.remove();
        ROLES.remove();
        PERMISSIONS.remove();
    }

}
