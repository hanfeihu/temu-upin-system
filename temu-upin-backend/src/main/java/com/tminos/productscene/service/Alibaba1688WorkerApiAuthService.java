package com.tminos.productscene.service;

import com.tminos.productscene.config.Alibaba1688WorkerApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class Alibaba1688WorkerApiAuthService {

    private final Alibaba1688WorkerApiProperties properties;

    public Alibaba1688WorkerApiAuthService(Alibaba1688WorkerApiProperties properties) {
        this.properties = properties;
    }

    public void requireAllowed(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new IllegalStateException("1688 worker API 已禁用");
        }
        String expected = properties.getKey();
        if (!StringUtils.hasText(expected)) {
            throw new IllegalStateException("1688 worker API key 未配置");
        }
        String actual = request == null ? null : request.getHeader("X-Worker-Key");
        if (!StringUtils.hasText(actual)) {
            actual = request == null ? null : request.getHeader("X-1688-Worker-Key");
        }
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("1688 worker key 无效");
        }
    }
}
