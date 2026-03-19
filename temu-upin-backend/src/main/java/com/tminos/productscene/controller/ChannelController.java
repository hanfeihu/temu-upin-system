package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ChannelDTO.*;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.ChannelService;
import com.tminos.productscene.service.ChannelCredentialService;
import com.tminos.productscene.service.ImageGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {
    
    private final ChannelService channelService;
    private final ChannelCredentialService channelCredentialService;
    private final ImageGenerationService imageGenerationService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getAllChannels() {
        return ResponseEntity.ok(ApiResponse.success(channelService.getAllChannels()));
    }
    
    @GetMapping("/enabled")
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getEnabledChannels() {
        return ResponseEntity.ok(ApiResponse.success(channelService.getEnabledChannels()));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> getChannel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getChannel(id)));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<ChannelResponse>> createChannel(@RequestBody CreateChannelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Channel created", channelService.createChannel(request)));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> updateChannel(
            @PathVariable Long id, 
            @RequestBody UpdateChannelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Channel updated", channelService.updateChannel(id, request)));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteChannel(@PathVariable Long id) {
        channelService.deleteChannel(id);
        return ResponseEntity.ok(ApiResponse.success("Channel deleted", null));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<ChannelTestResponse>> testChannel(
            @PathVariable Long id,
            @RequestBody(required = false) ChannelTestRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(imageGenerationService.testChannel(id, request)));
    }

    // Initialize missing apiKey/apiSecret for channels from environment variables.
    // This writes secrets into DB, so only use it in trusted environments.
    @PostMapping("/init-credentials")
    public ResponseEntity<ApiResponse<ChannelCredentialService.InitResult>> initCredentials(
            @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite) {
        return ResponseEntity.ok(ApiResponse.success(
                "Credentials initialized",
                channelCredentialService.syncCredentialsFromEnvironment(overwrite)
        ));
    }
}
