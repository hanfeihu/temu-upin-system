package com.tminos.productscene.util;

import org.springframework.util.StringUtils;

public final class TextAiUrlHelper {

    private TextAiUrlHelper() {
    }

    public static String chatCompletionsUrl(String baseUrl, String defaultBaseUrl) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim() : defaultBaseUrl;
        if (!StringUtils.hasText(base)) {
            base = "https://chatbot.tminos.com";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1/chat/completions") || base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }
}
