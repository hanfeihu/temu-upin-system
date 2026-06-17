package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.AIImageTranslateService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/ai-image-translate")
public class AIImageTranslateController {

    private final AIImageTranslateService aiImageTranslateService;

    public AIImageTranslateController(AIImageTranslateService aiImageTranslateService) {
        this.aiImageTranslateService = aiImageTranslateService;
    }

    @PostMapping(value = "/render", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> renderTranslatedImage(
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "sourceLanguage", required = false) String sourceLanguage,
            @RequestParam(value = "targetLanguage", required = false) String targetLanguage,
            @RequestParam(value = "marketplace", required = false) String marketplace,
            @RequestParam(value = "instructions", required = false) String instructions,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "quality", required = false) String quality,
            @RequestParam(value = "outputFormat", required = false) String outputFormat,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "uploadToOss", required = false) Boolean uploadToOss
    ) {
        MultipartFile actualImage = image != null && !image.isEmpty() ? image : file;
        try {
            AIImageTranslateService.TranslateResult result = aiImageTranslateService.translate(
                    actualImage,
                    imageUrl,
                    new AIImageTranslateService.RequestOptions(
                            sourceLanguage,
                            targetLanguage,
                            marketplace,
                            instructions,
                            size,
                            quality,
                            outputFormat,
                            model,
                            uploadToOss
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(result.contentType()));
            headers.setCacheControl(CacheControl.noStore());
            headers.setContentDisposition(ContentDisposition.inline().filename(result.filename()).build());
            headers.setContentLength(result.imageBytes().length);
            headers.set("X-AI-Image-Model", result.model());
            if (StringUtils.hasText(result.requestId())) {
                headers.set("X-AI-Request-Id", result.requestId());
            }
            if (StringUtils.hasText(result.ossUrl())) {
                headers.set("X-AI-Image-Url", result.ossUrl());
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(result.imageBytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            String message = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.internalServerError().body(ApiResponse.error(message));
        }
    }
}
