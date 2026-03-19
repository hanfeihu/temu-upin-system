package com.tminos.productscene.dto;

import java.util.List;

public class StabilityFusionDTO {

    public static class FuseRequest {
        private List<String> imageUrls;
        private String prompt;
        private String negativePrompt;
        private Double strength;
        private Integer width;
        private Integer height;
        private String model;
        private Long seed;
        private Double cfgScale;
        private String outputFormat;

        public List<String> getImageUrls() {
            return imageUrls;
        }

        public void setImageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public String getNegativePrompt() {
            return negativePrompt;
        }

        public void setNegativePrompt(String negativePrompt) {
            this.negativePrompt = negativePrompt;
        }

        public Double getStrength() {
            return strength;
        }

        public void setStrength(Double strength) {
            this.strength = strength;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Long getSeed() {
            return seed;
        }

        public void setSeed(Long seed) {
            this.seed = seed;
        }

        public Double getCfgScale() {
            return cfgScale;
        }

        public void setCfgScale(Double cfgScale) {
            this.cfgScale = cfgScale;
        }

        public String getOutputFormat() {
            return outputFormat;
        }

        public void setOutputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
        }
    }

    public static class FuseResponse {
        private String imageUrl;
        private String provider;
        private String model;

        public FuseResponse() {}

        public FuseResponse(String imageUrl, String provider, String model) {
            this.imageUrl = imageUrl;
            this.provider = provider;
            this.model = model;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
