package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;

public class ParserTestDTO {

    public static class ParseRequest {
        @NotBlank(message = "html 不能为空")
        private String html;

        public ParseRequest() {
        }

        public ParseRequest(String html) {
            this.html = html;
        }

        public String getHtml() {
            return html;
        }

        public void setHtml(String html) {
            this.html = html;
        }
    }

    public static class ParseResponse {
        private String runId;
        private String parserType;
        private boolean success;
        private String errorMessage;
        private String createdAt;
        private String html;
        private java.util.Map<String, Object> result;

        public ParseResponse() {
        }

        public String getRunId() {
            return runId;
        }

        public void setRunId(String runId) {
            this.runId = runId;
        }

        public String getParserType() {
            return parserType;
        }

        public void setParserType(String parserType) {
            this.parserType = parserType;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getHtml() {
            return html;
        }

        public void setHtml(String html) {
            this.html = html;
        }

        public java.util.Map<String, Object> getResult() {
            return result;
        }

        public void setResult(java.util.Map<String, Object> result) {
            this.result = result;
        }
    }
}
