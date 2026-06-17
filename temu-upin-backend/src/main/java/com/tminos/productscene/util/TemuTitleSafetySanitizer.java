package com.tminos.productscene.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class TemuTitleSafetySanitizer {

    private TemuTitleSafetySanitizer() {
    }

    public static String removeUnsupportedPreciousMetalWords(String title) {
        if (!StringUtils.hasText(title)) {
            return title;
        }
        String cleaned = title.trim();
        cleaned = cleaned
                .replace("玫瑰金", "粉色")
                .replace("香槟金", "黄色")
                .replace("五金", "配件")
                .replace("金属", "材质")
                .replace("黄金", "黄色")
                .replace("金色", "黄色")
                .replace("镀金", "黄色")
                .replace("纯银", "灰色")
                .replace("925银", "灰色")
                .replace("银饰", "饰品")
                .replace("白银", "灰色")
                .replace("银色", "灰色")
                .replace("镀银", "灰色")
                .replace("金", "黄色")
                .replace("银", "灰色");

        cleaned = replaceEnglishMetalWord(cleaned, "rose gold", "pink");
        cleaned = replaceEnglishMetalWord(cleaned, "champagne gold", "yellow");
        cleaned = replaceEnglishMetalWord(cleaned, "gold plated", "yellow");
        cleaned = replaceEnglishMetalWord(cleaned, "gold-plated", "yellow");
        cleaned = replaceEnglishMetalWord(cleaned, "golden", "yellow");
        cleaned = replaceEnglishMetalWord(cleaned, "gold", "yellow");
        cleaned = replaceEnglishMetalWord(cleaned, "sterling silver", "gray");
        cleaned = replaceEnglishMetalWord(cleaned, "925 silver", "gray");
        cleaned = replaceEnglishMetalWord(cleaned, "silver plated", "gray");
        cleaned = replaceEnglishMetalWord(cleaned, "silver-plated", "gray");
        cleaned = replaceEnglishMetalWord(cleaned, "silvery", "gray");
        cleaned = replaceEnglishMetalWord(cleaned, "silver", "gray");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String replaceEnglishMetalWord(String input, String word, String replacement) {
        String pattern = "(?i)(?<![A-Za-z0-9])" + java.util.regex.Pattern.quote(word) + "(?![A-Za-z0-9])";
        return input.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(replacement));
    }

    public static boolean containsUnsupportedPreciousMetalWord(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return title.contains("金")
                || title.contains("银")
                || lower.matches(".*(?<![a-z0-9])(rose gold|champagne gold|gold plated|gold-plated|golden|gold|sterling silver|925 silver|silver plated|silver-plated|silvery|silver)(?![a-z0-9]).*");
    }
}
