package com.tminos.productscene.service;

import com.tminos.productscene.entity.AIChannel;
import com.tminos.productscene.repository.AIChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelCredentialService {

    private final AIChannelRepository channelRepository;
    private final Environment environment;

    @Transactional
    public InitResult fillCredentialsFromEnvironmentIfMissing() {
        return syncCredentialsFromEnvironment(false);
    }

    @Transactional
    public InitResult syncCredentialsFromEnvironment(boolean overwriteExisting) {
        int updatedChannels = 0;

        String stabilityKey = environment.getProperty("STABILITY_API_KEY");
        if (StringUtils.hasText(stabilityKey)) {
            updatedChannels += updatePlatformCredentials("stability", stabilityKey, null, overwriteExisting);
        }

        String volcAk = environment.getProperty("VOLCENGINE_API_KEY");
        String volcSk = environment.getProperty("VOLCENGINE_API_SECRET");
        if (StringUtils.hasText(volcAk) || StringUtils.hasText(volcSk)) {
            updatedChannels += updatePlatformCredentials("volcengine", volcAk, volcSk, overwriteExisting);
            updatedChannels += updatePlatformCredentials("jimeng_i2i", volcAk, volcSk, overwriteExisting);
            updatedChannels += updatePlatformCredentials("jimeng_t2i", volcAk, volcSk, overwriteExisting);
        }

        String runwayKey = environment.getProperty("RUNWAY_API_KEY");
        if (StringUtils.hasText(runwayKey)) {
            updatedChannels += updatePlatformCredentials("runway", runwayKey, null, overwriteExisting);
        }

        if (updatedChannels > 0) {
            log.info("Initialized missing channel credentials: {} channel(s) updated", updatedChannels);
        } else {
            log.info("No missing channel credentials to initialize (env empty or already set)");
        }

        return new InitResult(updatedChannels);
    }

    private int updatePlatformCredentials(String platform, String apiKey, String apiSecret, boolean overwriteExisting) {
        List<AIChannel> channels = channelRepository.findByPlatformOrderBySortOrderAsc(platform);
        if (channels.isEmpty()) {
            return 0;
        }

        boolean changed = false;
        int updated = 0;
        for (AIChannel channel : channels) {
            boolean updatedThis = false;
            if (StringUtils.hasText(apiKey) && (overwriteExisting || !StringUtils.hasText(channel.getApiKey()))) {
                channel.setApiKey(apiKey);
                updatedThis = true;
            }
            if (StringUtils.hasText(apiSecret) && (overwriteExisting || !StringUtils.hasText(channel.getApiSecret()))) {
                channel.setApiSecret(apiSecret);
                updatedThis = true;
            }
            if (updatedThis) {
                updated++;
                changed = true;
            }
        }

        if (changed) {
            channelRepository.saveAll(channels);
            log.info("Filled missing credentials for platform: {} ({} channel(s) updated)", platform, updated);
        }

        return updated;
    }

    public record InitResult(int updatedChannels) {}

    @Transactional
    public InitResult initPlatformCredentials(String platform, String apiKey, String apiSecret, boolean overwriteExisting) {
        log.info("Channel credentials init requested: platform={}, overwriteExisting={}", platform, overwriteExisting);
        int updated = 0;
        updated += updatePlatformCredentials(platform, apiKey, apiSecret, overwriteExisting);

        // volcengine AK/SK is also used for Jimeng I2I/T2I in this project.
        if ("volcengine".equals(platform)) {
            updated += updatePlatformCredentials("jimeng_i2i", apiKey, apiSecret, overwriteExisting);
            updated += updatePlatformCredentials("jimeng_t2i", apiKey, apiSecret, overwriteExisting);
        }

        if (updated > 0) {
            log.info("Initialized platform credentials: platform={}, updatedChannels={}", platform, updated);
        } else {
            log.info("No channel credentials updated for platform={} (blank input or already set)", platform);
        }

        return new InitResult(updated);
    }
}
