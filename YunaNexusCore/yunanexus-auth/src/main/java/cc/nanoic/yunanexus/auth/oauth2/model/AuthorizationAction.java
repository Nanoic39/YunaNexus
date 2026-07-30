package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * 用户在授权页面点击 "同意" 或 "拒绝" 的动作.
 */
@Data
public class AuthorizationAction {

    /** "approve" 或 "deny" */
    private String action;

    public boolean isApproved() {
        return "approve".equals(action);
    }

    public boolean isDenied() {
        return "deny".equals(action);
    }
}
