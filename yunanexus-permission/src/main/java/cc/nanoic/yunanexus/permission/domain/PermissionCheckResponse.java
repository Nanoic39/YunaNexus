package cc.nanoic.yunanexus.permission.domain;

import lombok.Data;

/**
 * 鉴权判定结果。
 *
 * @author yunanexus
 */
@Data
public class PermissionCheckResponse {

    /** 是否允许 */
    private boolean allowed;

    public static PermissionCheckResponse of(boolean allowed) {
        PermissionCheckResponse response = new PermissionCheckResponse();
        response.setAllowed(allowed);
        return response;
    }
}
