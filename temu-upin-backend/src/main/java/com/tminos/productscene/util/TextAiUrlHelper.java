package com.tminos.productscene.util;

import org.springframework.util.StringUtils;

public final class TextAiUrlHelper {

    private TextAiUrlHelper() {
    }

    public static String chatCompletionsUrl(String baseUrl, String defaultBaseUrl) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim() : defaultBaseUrl;
        if (!StringUtils.hasText(base)) {
            base = "https://api.openai.com";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1/chat/completions") || base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1/images/edits")) {
            base = base.substring(0, base.length() - "/images/edits".length());
        } else if (base.endsWith("/images/edits")) {
            base = base.substring(0, base.length() - "/images/edits".length());
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    public static String imageEditsUrl(String baseUrl, String defaultBaseUrl) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim() : defaultBaseUrl;
        if (!StringUtils.hasText(base)) {
            base = "https://api.openai.com";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1/images/edits") || base.endsWith("/images/edits")) {
            return base;
        }
        if (base.endsWith("/v1/chat/completions") || base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/images/edits";
        }
        if (base.endsWith("/v1")) {
            return base + "/images/edits";
        }
        return base + "/v1/images/edits";
    }

    public static String imageGenerationsUrl(String baseUrl, String defaultBaseUrl) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim() : defaultBaseUrl;
        if (!StringUtils.hasText(base)) {
            base = "https://api.openai.com";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1/images/generations") || base.endsWith("/images/generations")) {
            return base;
        }
        if (base.endsWith("/v1/images/edits") || base.endsWith("/images/edits")) {
            return base.substring(0, base.length() - "/edits".length()) + "/generations";
        }
        if (base.endsWith("/v1/chat/completions") || base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/images/generations";
        }
        if (base.endsWith("/v1")) {
            return base + "/images/generations";
        }
        return base + "/v1/images/generations";
    }
}
