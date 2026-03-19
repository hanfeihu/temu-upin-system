package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ChannelDTO.InitPlatformCredentialsRequest;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.ChannelCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/channels/credentials")
@RequiredArgsConstructor
public class ChannelCredentialController {

    private final ChannelCredentialService channelCredentialService;

    @PostMapping("/platform/{platform}")
    public ResponseEntity<ApiResponse<ChannelCredentialService.InitResult>> initPlatformCredentials(
            @PathVariable String platform,
            @RequestBody InitPlatformCredentialsRequest request
    ) {
        boolean overwrite = request != null && Boolean.TRUE.equals(request.getOverwriteExisting());
        String apiKey = request != null ? request.getApiKey() : null;
        String apiSecret = request != null ? request.getApiSecret() : null;

        ChannelCredentialService.InitResult result = channelCredentialService
                .initPlatformCredentials(platform, apiKey, apiSecret, overwrite);

        return ResponseEntity.ok(ApiResponse.success("Credentials initialized", result));
    }
}
