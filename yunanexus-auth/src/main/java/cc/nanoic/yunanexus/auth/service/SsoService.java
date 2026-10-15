package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.TokenExchangeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface SsoService {

    void establish(LoginResult result, HttpServletRequest request, HttpServletResponse response);

    void clear(HttpServletRequest request, HttpServletResponse response);

    String authorize(AuthorizeRequest request, String cookieValue);

    LoginResult exchange(TokenExchangeRequest request);

    String readCookie(HttpServletRequest request);
}
