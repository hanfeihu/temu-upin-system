package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.util.List;

public class SyncConfigDTO {

    @Data
    public static class ConfigItem {
        private Long id;
        private String shopId;
        private String configKey;
        private String configValue;
        private String configDesc;
    }

    @Data
    public static class SaveRequest {
        private String shopId;
        private List<ConfigItem> configs;
    }

    @Data
    public static class ConfigResponse {
        private String shopId;
        private List<ConfigItem> configs;
    }
}
