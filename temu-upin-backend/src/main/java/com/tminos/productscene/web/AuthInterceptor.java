package com.tminos.productscene.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.PlatformAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final PlatformAuthService platformAuthService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(PlatformAuthService platformAuthService, ObjectMapper objectMapper) {
        this.platformAuthService = platformAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!platformAuthService.isEnabled()) {
            return true;
        }
        if (request == null || response == null) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestUri = request.getRequestURI();
        if (requestUri == null || !requestUri.startsWith("/api/")) {
            return true;
        }
        if (isPublicPath(requestUri)) {
            return true;
        }

        PlatformAuthService.AuthenticatedUser user = platformAuthService.authenticate(platformAuthService.extractToken(request));
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error("未登录或登录已过期"));
            return false;
        }

        request.setAttribute(PlatformAuthService.CURRENT_USER_ATTR, user);
        return true;
    }

    private boolean isPublicPath(String requestUri) {
        return requestUri.startsWith("/api/auth/login")
                || requestUri.startsWith("/api/ocr/");
    }
}
