package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.PlatformAuthDTO;
import com.tminos.productscene.service.PlatformAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    public PlatformAuthController(PlatformAuthService platformAuthService) {
        this.platformAuthService = platformAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<PlatformAuthDTO.LoginResponse>> login(
            @Valid @RequestBody PlatformAuthDTO.LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(platformAuthService.login(request.getUsername(), request.getPassword())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PlatformAuthDTO.CurrentUserResponse>> me(HttpServletRequest request) {
        Object currentUser = request == null ? null : request.getAttribute(PlatformAuthService.CURRENT_USER_ATTR);
        if (currentUser instanceof PlatformAuthService.AuthenticatedUser authenticatedUser) {
            return ResponseEntity.ok(ApiResponse.success(platformAuthService.currentUser(authenticatedUser)));
        }
        return ResponseEntity.status(401).body(ApiResponse.error("未登录或登录已过期"));
    }
}
