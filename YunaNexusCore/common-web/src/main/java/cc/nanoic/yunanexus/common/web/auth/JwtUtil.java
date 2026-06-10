package cc.nanoic.yunanexus.common.web.auth;

import cn.hutool.core.util.HexUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;

import java.util.*;

public class JwtUtil {

    public static final String KEY_UUID = "uuid";
    public static final String KEY_GLOBAL_ID = "globalId";
    public static final String KEY_ROLES = "roles";
    public static final String KEY_PERMISSIONS = "permissions";

    public static JwtPayload parseToken(String token, byte[] secret) {
        if (!JWTUtil.verify(token, secret)) {
            return null;
        }
        JWT jwt = JWTUtil.parseToken(token);
        JWTPayload payload = jwt.getPayload();

        JwtPayload result = new JwtPayload();
        result.uuid = (String) payload.getClaim(KEY_UUID);
        result.globalId = HexUtil.decodeHex((String) payload.getClaim(KEY_GLOBAL_ID));

        Object rolesObj = payload.getClaim(KEY_ROLES);
        result.roles = toSet(rolesObj);

        Object permsObj = payload.getClaim(KEY_PERMISSIONS);
        result.permissions = toSet(permsObj);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> toSet(Object obj) {
        if (obj instanceof List) {
            return new HashSet<>((List<String>) obj);
        }
        return Collections.emptySet();
    }

    public static String createToken(String uuid, byte[] globalId,
                                     Set<String> roles, Set<String> permissions,
                                     byte[] secret, long expireMs) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(KEY_UUID, uuid);
        claims.put(KEY_GLOBAL_ID, HexUtil.encodeHexStr(globalId));
        claims.put(KEY_ROLES, new ArrayList<>(roles));
        claims.put(KEY_PERMISSIONS, new ArrayList<>(permissions));

        long now = System.currentTimeMillis();
        claims.put(JWTPayload.ISSUED_AT, now);
        claims.put(JWTPayload.EXPIRES_AT, now + expireMs);

        return JWTUtil.createToken(claims, secret);
    }

    public static class JwtPayload {
        public String uuid;
        public byte[] globalId;
        public Set<String> roles;
        public Set<String> permissions;
    }


}
