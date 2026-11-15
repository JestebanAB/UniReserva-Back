package com.unireserva.back.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.StringUtils;

public class JwtCookieBearerTokenResolver implements BearerTokenResolver {
    private final String cookieName;

    public JwtCookieBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith("/auth/login")
                || path.endsWith("/auth/register")
                || path.endsWith("/auth/csrf")) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String token = null;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                if (token != null) {
                    throw new OAuth2AuthenticationException(new BearerTokenError(
                            BearerTokenErrorCodes.INVALID_REQUEST,
                            null,
                            "Multiple authentication cookies were provided",
                            null));
                }
                token = cookie.getValue();
            }
        }

        return StringUtils.hasText(token) ? token : null;
    }
}
