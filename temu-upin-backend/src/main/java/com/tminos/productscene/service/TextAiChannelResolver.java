package com.tminos.productscene.service;

import com.tminos.productscene.entity.AIChannel;
import com.tminos.productscene.entity.AIChannelBusinessConfig;
import com.tminos.productscene.repository.AIChannelBusinessConfigRepository;
import com.tminos.productscene.repository.AIChannelRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TextAiChannelResolver {

    private final AIChannelBusinessConfigRepository businessConfigRepository;
    private final AIChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public ResolvedChannel resolve(String businessCode, String fallbackBaseUrl, String fallbackApiKey, String fallbackModel) {
        if (StringUtils.hasText(businessCode)) {
            AIChannelBusinessConfig config = businessConfigRepository.findByBusinessCodeAndEnabledTrue(businessCode.trim()).orElse(null);
            if (config != null && config.getChannelId() != null) {
                AIChannel channel = channelRepository.findById(config.getChannelId()).orElse(null);
                if (channel != null && Boolean.TRUE.equals(channel.getEnabled())) {
                    return ResolvedChannel.builder()
                            .businessCode(config.getBusinessCode())
                            .channelId(channel.getId())
                            .channelName(channel.getName())
                            .baseUrl(firstNonBlank(channel.getBaseUrl(), fallbackBaseUrl))
                            .apiKey(firstNonBlank(channel.getApiKey(), fallbackApiKey))
                            .model(firstNonBlank(channel.getModel(), fallbackModel))
                            .fromBusinessConfig(true)
                            .build();
                }
            }
        }

        return ResolvedChannel.builder()
                .businessCode(businessCode)
                .baseUrl(trimToNull(fallbackBaseUrl))
                .apiKey(trimToNull(fallbackApiKey))
                .model(trimToNull(fallbackModel))
                .fromBusinessConfig(false)
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trimToNull(second);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Getter
    @Builder
    public static class ResolvedChannel {
        private String businessCode;
        private Long channelId;
        private String channelName;
        private String baseUrl;
        private String apiKey;
        private String model;
        private boolean fromBusinessConfig;
    }
}
