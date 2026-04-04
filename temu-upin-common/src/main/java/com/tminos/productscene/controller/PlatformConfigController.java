package com.tminos.productscene.controller;

import com.tminos.productscene.dto.PlatformConfigDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.PlatformConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/config")
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;

    public PlatformConfigController(PlatformConfigService platformConfigService) {
        this.platformConfigService = platformConfigService;
    }

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<PlatformConfigDTO.ListProfilesResponse>> listProfiles() {
        List<PlatformConfigDTO.ProfileResponse> profiles = platformConfigService.listProfiles();
        return ResponseEntity.ok(ApiResponse.success(new PlatformConfigDTO.ListProfilesResponse(profiles)));
    }

    @PostMapping("/profiles")
    public ResponseEntity<ApiResponse<PlatformConfigDTO.ProfileResponse>> create(@RequestBody PlatformConfigDTO.SaveProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success(platformConfigService.createProfile(req)));
    }

    @PutMapping("/profiles/{id}")
    public ResponseEntity<ApiResponse<PlatformConfigDTO.ProfileResponse>> update(
            @PathVariable Long id,
            @RequestBody PlatformConfigDTO.SaveProfileRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success(platformConfigService.updateProfile(id, req)));
    }

    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        platformConfigService.deleteProfile(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/profiles/default")
    public ResponseEntity<ApiResponse<Void>> setDefault(@RequestBody PlatformConfigDTO.SetDefaultRequest req) {
        if (req == null || req.getProfileId() == null) {
            return ResponseEntity.ok(ApiResponse.error("profileId is required"));
        }
        platformConfigService.setDefault(req.getProfileId());
        return ResponseEntity.ok(ApiResponse.success("OK", null));
    }
}
