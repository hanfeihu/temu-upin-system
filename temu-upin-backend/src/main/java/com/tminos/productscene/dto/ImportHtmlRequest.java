package com.tminos.productscene.dto;

public class ImportHtmlRequest {
    private String html;
    private String extractedJson;

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
}
