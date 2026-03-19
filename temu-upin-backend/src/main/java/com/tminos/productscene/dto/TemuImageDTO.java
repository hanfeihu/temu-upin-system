package com.tminos.productscene.dto;

public class TemuImageDTO {

    public static class TranslateImageRequest {
        private String imageUrl;
        private String sourceLanguage;
        private String targetLang;
        /** translator provider: temu | aliyun */
        private String provider;
        private Boolean containDetail;
        private String scene;
        private Boolean uploadToOss;

        public TranslateImageRequest() {
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getSourceLanguage() {
            return sourceLanguage;
        }

        public void setSourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
        }

        public String getTargetLang() {
            return targetLang;
        }

        public void setTargetLang(String targetLang) {
            this.targetLang = targetLang;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Boolean getContainDetail() {
            return containDetail;
        }

        public void setContainDetail(Boolean containDetail) {
            this.containDetail = containDetail;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public Boolean getUploadToOss() {
            return uploadToOss;
        }

        public void setUploadToOss(Boolean uploadToOss) {
            this.uploadToOss = uploadToOss;
        }
    }

    public static class TranslateImageResponse {
        private String originalUrl;
        private String uploadedUrl;
        private String taskId;
        private String translatedUrl;
        private String storedUrl;
        private String message;

        public TranslateImageResponse() {
        }

        public TranslateImageResponse(String originalUrl, String uploadedUrl, String taskId, String translatedUrl, String storedUrl, String message) {
            this.originalUrl = originalUrl;
            this.uploadedUrl = uploadedUrl;
            this.taskId = taskId;
            this.translatedUrl = translatedUrl;
            this.storedUrl = storedUrl;
            this.message = message;
        }

        public String getOriginalUrl() {
            return originalUrl;
        }

        public void setOriginalUrl(String originalUrl) {
            this.originalUrl = originalUrl;
        }

        public String getUploadedUrl() {
            return uploadedUrl;
        }

        public void setUploadedUrl(String uploadedUrl) {
            this.uploadedUrl = uploadedUrl;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getTranslatedUrl() {
            return translatedUrl;
        }

        public void setTranslatedUrl(String translatedUrl) {
            this.translatedUrl = translatedUrl;
        }

        public String getStoredUrl() {
            return storedUrl;
        }

        public void setStoredUrl(String storedUrl) {
            this.storedUrl = storedUrl;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
