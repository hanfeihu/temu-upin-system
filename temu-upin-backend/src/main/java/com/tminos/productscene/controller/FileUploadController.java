package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import com.tminos.productscene.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/upload")
@Slf4j
public class FileUploadController {
    
    private final OssService ossService;

    public FileUploadController(OssService ossService) {
        this.ossService = ossService;
    }
    
    @PostMapping("/image")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
        }
        
        try {
            String originalFilename = file.getOriginalFilename();
            String url = ossService.uploadSkuImage(file);
            
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("filename", url.substring(url.lastIndexOf('/') + 1));
            result.put("originalName", originalFilename);
            result.put("size", String.valueOf(file.getSize()));
            
            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", result));
            
        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("File upload failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> uploadMultipleImages(
            @RequestParam("files") MultipartFile[] files) {
        
        List<Map<String, String>> uploadedFiles = new ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalFilename = file.getOriginalFilename();
            try {
                String url = ossService.uploadSkuImage(file);
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("url", url);
                fileInfo.put("filename", url.substring(url.lastIndexOf('/') + 1));
                fileInfo.put("originalName", originalFilename);
                fileInfo.put("size", String.valueOf(file.getSize()));
                uploadedFiles.add(fileInfo);
            } catch (Exception e) {
                log.error("File upload failed: {}", originalFilename, e);
            }
        }
        
        if (uploadedFiles.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No files were uploaded"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Files uploaded successfully", uploadedFiles));
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + e.getMessage()));
    }
}
