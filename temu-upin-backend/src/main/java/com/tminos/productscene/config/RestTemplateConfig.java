package com.tminos.productscene.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.tminos.productscene.config.AIAlibaba1688SelectionReportConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class RestTemplateConfig {

    private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Qualifier("aiLongRestTemplate")
    public RestTemplate aiLongRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(300_000);
        return new RestTemplate(f);
    }

    @Bean
    @Qualifier("alibaba1688SelectionAiRestTemplate")
    public RestTemplate alibaba1688SelectionAiRestTemplate(AIAlibaba1688SelectionReportConfig config) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        int readTimeoutMs = normalizeTimeoutMs(config == null ? null : config.getTimeoutMs(), 240_000);
        f.setConnectTimeout(Math.min(readTimeoutMs, 10_000));
        f.setReadTimeout(readTimeoutMs);
        return new RestTemplate(f);
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DEFAULT_DATE_TIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DEFAULT_DATE_TIME_FORMATTER));
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private int normalizeTimeoutMs(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(1_000, value);
    }
}
