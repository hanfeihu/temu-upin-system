package com.tminos.productscene.service;

import com.tminos.productscene.config.PlatformAuthProperties;
import com.tminos.productscene.dto.PlatformAuthDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class PlatformAuthService {

    public static final String CURRENT_USER_ATTR = "platformAuthUser";

    public record AuthenticatedUser(String username, long expiresAt) {}

    private final PlatformAuthProperties properties;

    public PlatformAuthService(PlatformAuthProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public PlatformAuthDTO.LoginResponse login(String username, String password) {
        if (!matchesConfiguredAccount(username, password)) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        long expiresAt = Instant.now().plusSeconds(Math.max(1L, properties.getTokenTtlHours()) * 3600L).getEpochSecond();
        String accessToken = issueToken(properties.getUsername(), expiresAt);

        PlatformAuthDTO.LoginResponse response = new PlatformAuthDTO.LoginResponse();
        response.setTokenType("Bearer");
        response.setAccessToken(accessToken);
        response.setUsername(properties.getUsername());
        response.setDisplayName("平台管理员");
        response.setExpiresAt(expiresAt);
        return response;
    }

    public PlatformAuthDTO.CurrentUserResponse currentUser(AuthenticatedUser user) {
        PlatformAuthDTO.CurrentUserResponse response = new PlatformAuthDTO.CurrentUserResponse();
        response.setUsername(user.username());
        response.setDisplayName("平台管理员");
        response.setExpiresAt(user.expiresAt());
        return response;
    }

    public AuthenticatedUser authenticate(String token) {
        if (!properties.isEnabled()) {
            return new AuthenticatedUser(properties.getUsername(), Long.MAX_VALUE);
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 2) {
            return null;
        }

        String payloadPart = parts[0];
        String signaturePart = parts[1];
        String expectedSignature = sign(payloadPart);
        if (!MessageDigest.isEqual(signaturePart.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }

        int separator = payload.lastIndexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return null;
        }

        String username = payload.substring(0, separator);
        long expiresAt;
        try {
            expiresAt = Long.parseLong(payload.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }

        if (!properties.getUsername().equals(username)) {
            return null;
        }
        if (Instant.now().getEpochSecond() >= expiresAt) {
            return null;
        }

        return new AuthenticatedUser(username, expiresAt);
    }

    public String extractToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        String headerToken = request.getHeader("X-Tminos-Token");
        return StringUtils.hasText(headerToken) ? headerToken.trim() : null;
    }

    private boolean matchesConfiguredAccount(String username, String password) {
        if (!properties.isEnabled()) {
            return true;
        }
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPassword = password == null ? "" : password;
        return properties.getUsername().equals(normalizedUsername) && properties.getPassword().equals(normalizedPassword);
    }

    private String issueToken(String username, long expiresAt) {
        String payload = username + ":" + expiresAt;
        String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadPart + "." + sign(payloadPart);
    }

    private String sign(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成认证签名失败", e);
        }
    }
}
