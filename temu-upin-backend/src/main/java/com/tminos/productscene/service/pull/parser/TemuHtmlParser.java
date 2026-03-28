package com.tminos.productscene.service.pull.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemuHtmlParser {

    private final ObjectMapper objectMapper;

    public TemuHtmlParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Alibaba1688HtmlParser.ParsedProduct parse(String html) {
        Alibaba1688HtmlParser.ParsedProduct out = new Alibaba1688HtmlParser.ParsedProduct();
        out.setOriginalHtml(html);

        Document doc = Jsoup.parse(html == null ? "" : html);
        out.setProductName(extractTitle(doc));
        out.setProductId(extractProductId(html));
        out.setProductUrl(extractCanonicalUrl(doc));

        List<String> gallery = extractGalleryImages(html);
        if (!gallery.isEmpty()) {
            out.setProductMainImage(gallery.get(0));
            out.setCarouselImagesJson(toJson(gallery));
            out.setCarouselThumbImagesJson(toJson(gallery));
        } else {
            out.setCarouselImagesJson("[]");
            out.setCarouselThumbImagesJson("[]");
        }

        List<String> detailImages = extractDetailImages(html);
        out.setDetailImagesJson(toJson(detailImages));
        out.setSkuModelJson("[]");
        out.setSkuDataJson("[]");
        out.setAttributesDataJson("[]");
        out.setPriceStepsJson("[]");
        out.setOriginalContent(extractRawDataJson(html));
        return out;
    }

    public static boolean looksLikeTemuHtml(String html) {
        if (html == null || html.isBlank()) return false;
        String s = html.toLowerCase(Locale.ROOT);
        return s.contains("window.__pagecontext__")
                || s.contains("pagepath\":\"w/goods")
                || s.contains("window.rawdata=")
                || s.contains("kwcdn.com")
                || s.contains("temucdn.com");
    }

    private String extractTitle(Document doc) {
        if (doc == null) return null;
        String title = doc.title();
        if (title == null) return null;
        title = title.trim();
        if (title.isBlank()) return null;
        return title;
    }

    private String extractProductId(String html) {
        if (html == null) return null;
        Matcher m = Pattern.compile("goods[_A-Za-z]*id['\"]?\\s*[:=]\\s*['\"]?([A-Za-z0-9_-]{8,})['\"]?", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        Matcher canonical = Pattern.compile("/goods\\.html\\?_x_sessn_id=|/goods\\.html\\?goods_id=([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(html);
        if (canonical.find() && canonical.groupCount() >= 1) return canonical.group(1);
        return null;
    }

    private String extractCanonicalUrl(Document doc) {
        if (doc == null) return null;
        String href = doc.select("link[rel=canonical]").attr("href");
        return href == null || href.isBlank() ? null : href.trim();
    }

    private List<String> extractGalleryImages(String html) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return new ArrayList<>();
        Matcher preload = Pattern.compile("<link[^>]+rel=\"preload\"[^>]+href=\"(https://(?:img|aimg)\\.kwcdn\\.com/[^\"]+)\"[^>]+as=\"image\"", Pattern.CASE_INSENSITIVE).matcher(html);
        while (preload.find()) {
            String u = normalizeImageUrl(preload.group(1));
            if (u != null) out.add(u);
        }
        return new ArrayList<>(out);
    }

    private List<String> extractDetailImages(String html) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String rawData = extractRawDataJson(html);
        if (rawData == null || rawData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = objectMapper.readTree(rawData);
            collectDetailImages(root, out, 0);
        } catch (Exception ignored) {
        }
        return new ArrayList<>(out);
    }

    private String extractRawDataJson(String html) {
        if (html == null || html.isBlank()) return null;
        int idx = html.indexOf("window.rawData=");
        if (idx < 0) return null;
        int braceStart = html.indexOf('{', idx);
        if (braceStart < 0) return null;
        int depth = 0;
        boolean inStr = false;
        char strCh = 0;
        for (int i = braceStart; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == strCh) {
                    inStr = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true;
                strCh = c;
                continue;
            }
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return html.substring(braceStart, i + 1);
                }
            }
        }
        return null;
    }

    private void collectDetailImages(JsonNode node, LinkedHashSet<String> out, int depth) {
        if (node == null || node.isMissingNode() || depth > 12) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if (value != null && value.isTextual()) {
                    String text = normalizeImageUrl(value.asText());
                    if (text != null && key.contains("detail")) {
                        out.add(text);
                    }
                } else {
                    collectDetailImages(value, out, depth + 1);
                }
            });
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectDetailImages(child, out, depth + 1);
            }
            return;
        }
        if (node.isTextual()) {
            String text = normalizeImageUrl(node.asText());
            if (text != null) {
                out.add(text);
            }
        }
    }

    private String normalizeImageUrl(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isBlank()) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (!(lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains(".gif"))) {
            return null;
        }
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        return s;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
