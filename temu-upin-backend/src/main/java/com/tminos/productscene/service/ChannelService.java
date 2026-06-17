package com.tminos.productscene.service;

import com.tminos.productscene.dto.ChannelDTO.*;
import com.tminos.productscene.entity.AIChannel;
import com.tminos.productscene.entity.AIChannelBusinessConfig;
import com.tminos.productscene.repository.AIChannelBusinessConfigRepository;
import com.tminos.productscene.repository.AIChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService {
    
    private final AIChannelRepository channelRepository;
    private final AIChannelBusinessConfigRepository businessConfigRepository;
    
    @Transactional(readOnly = true)
    public List<ChannelResponse> getAllChannels() {
        return channelRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ChannelResponse> getEnabledChannels() {
        return channelRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ChannelResponse getChannel(Long id) {
        AIChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        return toResponse(channel);
    }
    
    @Transactional
    public ChannelResponse createChannel(CreateChannelRequest request) {
        AIChannel channel = AIChannel.builder()
                .name(request.getName())
                .platform(request.getPlatform())
                .model(request.getModel())
                .apiKey(request.getApiKey())
                .apiSecret(request.getApiSecret())
                .baseUrl(request.getBaseUrl())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        
        channel = channelRepository.save(channel);
        if (StringUtils.hasText(request.getApiKey()) || StringUtils.hasText(request.getApiSecret())) {
            log.info("Channel credentials set on create: channelId={}, platform={}, model={}",
                    channel.getId(), channel.getPlatform(), channel.getModel());
        }
        log.info("Created channel: {}", channel.getName());
        return toResponse(channel);
    }
    
    @Transactional
    public ChannelResponse updateChannel(Long id, UpdateChannelRequest request) {
        AIChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));

        boolean willUpdateSecrets = StringUtils.hasText(request.getApiKey()) || StringUtils.hasText(request.getApiSecret());
        
        if (request.getName() != null) channel.setName(request.getName());
        if (request.getPlatform() != null) channel.setPlatform(request.getPlatform());
        if (request.getModel() != null) channel.setModel(request.getModel());
        // Only update secrets when explicitly provided (non-blank).
        // This allows UI to update other fields without accidentally clearing secrets.
        if (StringUtils.hasText(request.getApiKey())) channel.setApiKey(request.getApiKey());
        if (StringUtils.hasText(request.getApiSecret())) channel.setApiSecret(request.getApiSecret());
        if (request.getBaseUrl() != null) channel.setBaseUrl(request.getBaseUrl());
        if (request.getEnabled() != null) channel.setEnabled(request.getEnabled());
        if (request.getDescription() != null) channel.setDescription(request.getDescription());
        if (request.getSortOrder() != null) channel.setSortOrder(request.getSortOrder());
        
        channel = channelRepository.save(channel);
        if (willUpdateSecrets) {
            log.info("Channel credentials updated: channelId={}, platform={}, model={}",
                    channel.getId(), channel.getPlatform(), channel.getModel());
        }
        log.info("Updated channel: {}", channel.getName());
        return toResponse(channel);
    }
    
    @Transactional
    public void deleteChannel(Long id) {
        if (!channelRepository.existsById(id)) {
            throw new IllegalArgumentException("Channel not found: " + id);
        }
        channelRepository.deleteById(id);
        log.info("Deleted channel: {}", id);
    }

    @Transactional(readOnly = true)
    public List<BusinessConfigResponse> getBusinessConfigs() {
        return businessConfigRepository.findAllByOrderByIdAsc().stream()
                .map(this::toBusinessConfigResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BusinessConfigResponse createBusinessConfig(BusinessConfigRequest request) {
        validateBusinessConfigRequest(request);
        String businessCode = request.getBusinessCode().trim();
        if (businessConfigRepository.findByBusinessCode(businessCode).isPresent()) {
            throw new IllegalArgumentException("业务编码已存在: " + businessCode);
        }
        AIChannelBusinessConfig config = AIChannelBusinessConfig.builder()
                .businessName(request.getBusinessName().trim())
                .businessCode(businessCode)
                .channelId(request.getChannelId())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .description(request.getDescription())
                .build();
        return toBusinessConfigResponse(businessConfigRepository.save(config));
    }

    @Transactional
    public BusinessConfigResponse updateBusinessConfig(Long id, BusinessConfigRequest request) {
        AIChannelBusinessConfig config = businessConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Business config not found: " + id));
        if (request.getBusinessName() != null) {
            if (!StringUtils.hasText(request.getBusinessName())) {
                throw new IllegalArgumentException("业务名称不能为空");
            }
            config.setBusinessName(request.getBusinessName().trim());
        }
        if (request.getBusinessCode() != null) {
            if (!StringUtils.hasText(request.getBusinessCode())) {
                throw new IllegalArgumentException("业务编码不能为空");
            }
            String businessCode = request.getBusinessCode().trim();
            businessConfigRepository.findByBusinessCode(businessCode)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("业务编码已存在: " + businessCode);
                    });
            config.setBusinessCode(businessCode);
        }
        if (request.getChannelId() != null) {
            ensureChannelExists(request.getChannelId());
            config.setChannelId(request.getChannelId());
        }
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getDescription() != null) config.setDescription(request.getDescription());
        return toBusinessConfigResponse(businessConfigRepository.save(config));
    }

    @Transactional
    public void deleteBusinessConfig(Long id) {
        if (!businessConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("Business config not found: " + id);
        }
        businessConfigRepository.deleteById(id);
    }
    
    private ChannelResponse toResponse(AIChannel channel) {
        return ChannelResponse.builder()
                .id(channel.getId())
                .name(channel.getName())
                .platform(channel.getPlatform())
                .model(channel.getModel())
                .baseUrl(channel.getBaseUrl())
                .enabled(channel.getEnabled())
                .hasApiKey(StringUtils.hasText(channel.getApiKey()))
                .hasApiSecret(StringUtils.hasText(channel.getApiSecret()))
                .description(channel.getDescription())
                .sortOrder(channel.getSortOrder())
                .createdAt(channel.getCreatedAt().toString())
                .updatedAt(channel.getUpdatedAt() != null ? channel.getUpdatedAt().toString() : null)
                .build();
    }

    private void validateBusinessConfigRequest(BusinessConfigRequest request) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        if (!StringUtils.hasText(request.getBusinessName())) throw new IllegalArgumentException("业务名称不能为空");
        if (!StringUtils.hasText(request.getBusinessCode())) throw new IllegalArgumentException("业务编码不能为空");
        if (request.getChannelId() == null) throw new IllegalArgumentException("请选择渠道");
        ensureChannelExists(request.getChannelId());
    }

    private void ensureChannelExists(Long channelId) {
        if (!channelRepository.existsById(channelId)) {
            throw new IllegalArgumentException("Channel not found: " + channelId);
        }
    }

    private BusinessConfigResponse toBusinessConfigResponse(AIChannelBusinessConfig config) {
        AIChannel channel = config.getChannelId() == null ? null : channelRepository.findById(config.getChannelId()).orElse(null);
        return BusinessConfigResponse.builder()
                .id(config.getId())
                .businessName(config.getBusinessName())
                .businessCode(config.getBusinessCode())
                .channelId(config.getChannelId())
                .channelName(channel == null ? null : channel.getName())
                .channelModel(channel == null ? null : channel.getModel())
                .channelBaseUrl(channel == null ? null : channel.getBaseUrl())
                .enabled(config.getEnabled())
                .description(config.getDescription())
                .createdAt(config.getCreatedAt() == null ? null : config.getCreatedAt().toString())
                .updatedAt(config.getUpdatedAt() == null ? null : config.getUpdatedAt().toString())
                .build();
    }
}
