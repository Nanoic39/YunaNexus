package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * 返回给前端的授权详情 — 授权确认页面展示用.
 */
@Data
public class AuthorizationDetailsVO {

    /** 应用名称 */
    private String clientName;

    /** 应用描述 */
    private String description;

    /** 请求的授权范围 */
    private String scope;

    /** 回调地址 */
    private String redirectUri;

    /** 用户是否已登录 */
    private boolean isLoggedIn;
}
