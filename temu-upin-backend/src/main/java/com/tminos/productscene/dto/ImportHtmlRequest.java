package com.tminos.productscene.dto;

public class ImportHtmlRequest {
    private String html;
    private String extractedJson;
    private java.util.List<String> targetShopIds;

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getExtractedJson() {
        return extractedJson;
    }

    public void setExtractedJson(String extractedJson) {
        this.extractedJson = extractedJson;
    }

    public java.util.List<String> getTargetShopIds() {
        return targetShopIds;
    }

    public void setTargetShopIds(java.util.List<String> targetShopIds) {
        this.targetShopIds = targetShopIds;
    }
}
