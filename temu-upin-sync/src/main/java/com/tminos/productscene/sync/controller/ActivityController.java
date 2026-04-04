package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.ActivityDTO;
import com.tminos.productscene.sync.entity.TemuActivityEnrollment;
import com.tminos.productscene.sync.service.TemuActivityService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sync/activity")
public class ActivityController {

    private final TemuActivityService activityService;

    public ActivityController(TemuActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ActivityDTO.ActivityItem>>> listActivities(
            @RequestParam String shopId,
            @RequestParam(required = false) Integer activityType) {
        return ResponseEntity.ok(ApiResponse.success(activityService.listActivities(shopId, activityType)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ActivityDTO.SessionItem>>> listSessions(
            @RequestParam String shopId,
            @RequestParam Integer activityType,
            @RequestParam(required = false) Integer sessionStatus) {
        return ResponseEntity.ok(ApiResponse.success(activityService.listSessions(shopId, activityType, sessionStatus)));
    }

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<ActivityDTO.ActivityDetail>> activityDetail(
            @RequestParam String shopId,
            @RequestParam Integer activityType,
            @RequestParam(required = false) Long activityThematicId) {
        return ResponseEntity.ok(ApiResponse.success(
                activityService.getActivityDetail(shopId, activityType, activityThematicId)));
    }

    @PostMapping("/match-products")
    public ResponseEntity<ApiResponse<ActivityDTO.ProductMatchResponse>> matchProducts(
            @RequestBody ActivityDTO.ProductMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(activityService.matchProducts(request)));
    }

    @PostMapping("/sessions/query")
    public ResponseEntity<ApiResponse<ActivityDTO.SessionQueryResponse>> querySessions(
            @RequestBody ActivityDTO.SessionQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(activityService.querySessions(request)));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<ApiResponse<Page<TemuActivityEnrollment>>> listEnrollments(
            @RequestParam String shopId,
            @RequestParam(required = false) Integer activityType,
            @RequestParam(required = false) Integer enrollStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                activityService.listEnrollments(shopId, activityType, enrollStatus, page, pageSize)));
    }

    @GetMapping("/enrollments/{id}")
    public ResponseEntity<ApiResponse<ActivityDTO.EnrollmentItem>> enrollmentDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(activityService.getEnrollmentDetail(id)));
    }

    @PostMapping("/batch-enroll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchEnroll(
            @RequestBody ActivityDTO.BatchEnrollRequest request) {
        Map<String, Object> result = activityService.batchEnroll(request);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("批量报名完成", result));
        } else {
            String message = String.valueOf(result.getOrDefault("message", "批量报名失败"));
            return ResponseEntity.ok(ApiResponse.error("批量报名失败: " + message));
        }
    }
}
