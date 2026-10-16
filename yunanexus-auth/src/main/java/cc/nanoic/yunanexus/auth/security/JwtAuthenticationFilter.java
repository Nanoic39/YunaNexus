package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private java.util.function.BiPredicate<AuthenticatedIdentity,HttpServletRequest> clientAuthorization=(i,r)->false;
    public void setClientAuthorization(java.util.function.BiPredicate<AuthenticatedIdentity,HttpServletRequest> check){this.clientAuthorization=check;}
    private final SecurityErrorWriter securityErrorWriter;

    public JwtAuthenticationFilter(
            AuthenticationManager authenticationManager,
            SecurityErrorWriter securityErrorWriter) {
        this.authenticationManager = authenticationManager;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ")) {
            securityErrorWriter.write(response, 401, "未认证");
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            securityErrorWriter.write(response, 401, "未认证");
            return;
        }

        try {
            PreAuthenticatedAuthenticationToken preAuthToken = new PreAuthenticatedAuthenticationToken(token, null);
            Authentication authentication = authenticationManager.authenticate(preAuthToken);
            if(authentication.getPrincipal() instanceof AuthenticatedIdentity i && i.clientCode().startsWith("oauth_") && !clientAuthorization.test(i,request)) {
                securityErrorWriter.write(response,403,"应用无此接口权限");return;
            }
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BizException e) {
            SecurityContextHolder.clearContext();
            if (e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.UNAUTHORIZED.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.TOKEN_INVALID.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.TOKEN_EXPIRED.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.TOKEN_REVOKED.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.SESSION_NOT_FOUND.getCode()) {
                securityErrorWriter.write(response, 401, "未认证");
            } else if (e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.FORBIDDEN.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.CLIENT_DISABLED.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.CLIENT_NOT_FOUND.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.PLATFORM_AUTH_REVOKED.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.USER_DISABLED.getCode()
                || e.getCode() == cc.nanoic.yunanexus.common.web.constant.ErrorCode.USER_LOCKED.getCode()) {
                securityErrorWriter.write(response, 403, "无权限");
            } else {
                securityErrorWriter.write(response, 503, "服务不可用");
            }
            return;
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            securityErrorWriter.write(response, 401, "未认证");
            return;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            securityErrorWriter.write(response, 503, "服务不可用");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
