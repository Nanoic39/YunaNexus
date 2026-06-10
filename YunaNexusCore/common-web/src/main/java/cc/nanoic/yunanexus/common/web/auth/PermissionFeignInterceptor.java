package cc.nanoic.yunanexus.common.web.auth;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson2.JSON;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class PermissionFeignInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        if (!PermissionContext.hasIdentity()) {
            return;
        }
        template.header("X-User-Uuid", PermissionContext.getUuid());
        byte[] gid = PermissionContext.getGlobalId();
        if (gid != null) {
            template.header("X-User-Global-Id", HexUtil.encodeHexStr(gid));
        }
        template.header("X-User-Roles", JSON.toJSONString(PermissionContext.getRoles()));
        template.header("X-User-Permissions", JSON.toJSONString(PermissionContext.getPermissions()));
    }
}
