package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ParserTestDTO;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.ParserTestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/parser-test")
public class ParserTestController {

    private final ParserTestService parserTestService;

    public ParserTestController(ParserTestService parserTestService) {
        this.parserTestService = parserTestService;
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<ParserTestDTO.ParseResponse>> getRun(
            @PathVariable String runId
    ) {
        return ResponseEntity.ok(ApiResponse.success(parserTestService.getRun(runId)));
    }

    @PostMapping("/alibaba1688")
    public ResponseEntity<ApiResponse<ParserTestDTO.ParseResponse>> parseAlibaba1688(
            @Valid @RequestBody ParserTestDTO.ParseRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(parserTestService.parseAlibaba1688(request.getHtml())));
    }

    @PostMapping("/temu")
    public ResponseEntity<ApiResponse<ParserTestDTO.ParseResponse>> parseTemu(
            @Valid @RequestBody ParserTestDTO.ParseRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(parserTestService.parseTemu(request.getHtml())));
    }
}
