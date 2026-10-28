package cc.nanoic.yunanexus.file.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 调用方应用上下文。
 *
 * <p>应用身份来自网关注入的 {@code X-Client-Code}（JWT 的 clientCode）：
 * 为空或 {@code official} 表示官方前端，拥有跨应用管理权限；
 * 其余值代表三方应用，只能访问该用户在本应用下的文件。
 *
 * <p>用请求头而不是让 file 服务自己解 JWT：网关已经完成验签与解码，
 * 下游重复解析既浪费也容易与网关判断不一致。
 *
 * @author yunanexus
 */
public final class FileAccessContext {

    /** 官方前端的标志值 */
    public static final String OFFICIAL = "official";

    /** 网关注入的客户端标识请求头 */
    public static final String CLIENT_HEADER = "X-Client-Code";

    /** 网关注入的角色列表（逗号分隔） */
    public static final String ROLES_HEADER = "X-User-Roles";

    private FileAccessContext() {
    }

    /**
     * 取当前调用方应用 ID。
     *
     * @return 应用 ID；官方前端或无法判定时返回 {@code null}
     */
    public static String currentAppId() {
        String code = currentHeader();
        if (code == null || code.isBlank() || OFFICIAL.equalsIgnoreCase(code)) {
            return null;
        }
        return code.trim();
    }

    /**
     * 是否官方前端（可跨应用管理）。
     *
     * @return 官方前端返回 true
     */
    public static boolean isOfficial() {
        return currentAppId() == null;
    }

    /**
     * 取当前用户角色列表（网关按权限字段注入）。
     *
     * @return 角色代码列表；无法判定时返回空列表
     */
    public static java.util.List<String> currentRoles() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return java.util.List.of();
        }
        String raw = attrs.getRequest().getHeader(ROLES_HEADER);
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> roles = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
        return roles;
    }

    private static String currentHeader() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(CLIENT_HEADER);
    }
}
