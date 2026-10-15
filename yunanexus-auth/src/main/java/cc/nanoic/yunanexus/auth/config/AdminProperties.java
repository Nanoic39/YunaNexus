package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证中心管理后台配置。<p>
 *
 * 前缀 {@code yunanexus.auth.admin}。管理后台采用配置化管理员密钥鉴权：客户端在
 * 请求头 {@code X-Admin-Token} 中携带与 {@link #token} 一致的密钥即可访问
 * {@code /api/v1/auth/admin/**} 端点，与终端用户 JWT 完全解耦。<p>
 *
 * 安全约定：默认不配置密钥（{@code token} 为空），此时管理端点一律拒绝访问，
 * 必须显式配置密钥后才可用。生产环境通过环境变量 / Nacos 注入密钥，禁止硬编码。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.auth.admin")
public class AdminProperties {

    /** 管理后台密钥；为空表示管理后台未启用，所有管理端点拒绝访问 */
    private String token;

    /** 管理员密钥请求头名称 */
    private String headerName = "X-Admin-Token";
}
