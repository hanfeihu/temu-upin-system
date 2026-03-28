package com.tminos.productscene.service.pull.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        String rawData = extractRawDataJson(html);
        JsonNode rawRoot = readJson(rawData);

        out.setProductName(extractProductName(rawRoot, doc));
        out.setProductId(extractProductId(html));
        out.setProductUrl(extractCanonicalUrl(doc));
        out.setOriginalCategory(extractOriginalCategory(rawRoot));
        out.setProductCategory(extractProductCategory(rawRoot));
        out.setMonthlySales(extractMonthlySales(rawRoot));
        out.setAnnualSales(extractAnnualSales(rawRoot));
        out.setReviewCount(extractReviewCount(rawRoot));

        List<String> gallery = extractGalleryImages(html);
        if (!gallery.isEmpty()) {
            out.setProductMainImage(gallery.get(0));
            out.setCarouselImagesJson(toJson(gallery));
            out.setCarouselThumbImagesJson(toJson(gallery));
        } else {
            out.setCarouselImagesJson("[]");
            out.setCarouselThumbImagesJson("[]");
        }

        List<String> detailImages = extractDetailImages(rawRoot);
        out.setDetailImagesJson(toJson(detailImages));

        Map<String, Object> skuModel = extractSkuModel(rawRoot);
        out.setSkuModelJson(skuModel == null || skuModel.isEmpty() ? "[]" : toJson(skuModel));

        Map<String, Object> skuData = extractSkuData(rawRoot);
        out.setSkuDataJson(skuData == null || skuData.isEmpty() ? "[]" : toJson(skuData));

        Map<String, String> attributes = extractAttributes(rawRoot);
        out.setAttributesDataJson(attributes.isEmpty() ? "[]" : toJson(attributes));

        Map<String, Object> customMadeSpecs = extractCustomMadeSpecs(rawRoot);
        out.setCustomMadeSpecsJson(customMadeSpecs == null || customMadeSpecs.isEmpty() ? null : toJson(customMadeSpecs));

        out.setPriceStepsJson("[]");
        out.setOriginalContent(rawData);
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

    private String extractProductName(JsonNode root, Document doc) {
        String goodsName = text(child(child(storeNode(root), "goods"), "goodsName"));
        if (goodsName != null) {
            return goodsName;
        }

        String title = extractTitle(doc);
        if (title == null) {
            return null;
        }
        title = title.replaceAll("\\s*\\|\\s*Temu$", "").trim();
        title = title.replaceAll("\\s*\\|\\s*优惠点$", "").trim();
        return title.isBlank() ? null : title;
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

    private JsonNode readJson(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawData);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> extractDetailImages(JsonNode root) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (root == null || root.isMissingNode()) {
            return new ArrayList<>();
        }
        collectDetailImages(root, out, 0);
        return new ArrayList<>(out);
    }

    private String extractOriginalCategory(JsonNode root) {
        JsonNode crumbs = child(storeNode(root), "crumbOptList");
        if (crumbs == null || !crumbs.isArray() || crumbs.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode crumb : crumbs) {
            String title = text(child(crumb, "title"));
            if (title == null || "首页".equals(title)) {
                continue;
            }
            parts.add(title);
        }
        return parts.isEmpty() ? null : String.join(">", parts);
    }

    private String extractProductCategory(JsonNode root) {
        String original = extractOriginalCategory(root);
        if (original == null) {
            return null;
        }
        int idx = original.lastIndexOf('>');
        return idx >= 0 ? original.substring(idx + 1) : original;
    }

    private String extractMonthlySales(JsonNode root) {
        JsonNode saleInfo = child(child(storeNode(root), "goods"), "saleInfo");
        String value = text(child(saleInfo, "mallSalesTipNew"));
        if (value == null) {
            value = text(child(saleInfo, "mallSalesTip"));
        }
        if (value != null) {
            return normalizeMetricText(value);
        }

        JsonNode unit = child(child(child(child(storeNode(root), "moduleMap"), "mallModule"), "data"), "goodsSalesNumUnit");
        String joined = joinTextArray(unit);
        return joined == null ? null : normalizeMetricText(joined.replace(" / ", ""));
    }

    private String extractAnnualSales(JsonNode root) {
        return null;
    }

    private Integer extractReviewCount(JsonNode root) {
        JsonNode reviewStore = child(storeNode(root), "reviewStore");
        Integer reviewCount = integer(child(reviewStore, "mallReviewNum"));
        if (reviewCount != null) {
            return reviewCount;
        }
        return integer(child(reviewStore, "reviewNum"));
    }

    private Map<String, Object> extractSkuModel(JsonNode root) {
        JsonNode store = storeNode(root);
        JsonNode formatSkuData = child(store, "formatSkuData");
        JsonNode skuTypeValues = child(formatSkuData, "skuTypeValues");
        JsonNode skuArray = child(store, "sku");

        if ((skuTypeValues == null || !skuTypeValues.isArray()) && (skuArray == null || !skuArray.isArray())) {
            return null;
        }

        List<Map<String, Object>> props = new ArrayList<>();
        if (skuTypeValues != null && skuTypeValues.isArray()) {
            int propSort = 0;
            for (JsonNode typeNode : skuTypeValues) {
                String propName = text(child(typeNode, "type"));
                if (propName == null) {
                    propName = text(child(typeNode, "specKey"));
                }
                if (propName == null) {
                    continue;
                }

                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("name", propName);
                Integer fid = integer(child(typeNode, "specKeyId"));
                if (fid != null) {
                    prop.put("fid", fid);
                }
                prop.put("sort", propSort++);

                List<Map<String, Object>> values = new ArrayList<>();
                JsonNode valuesNode = child(typeNode, "values");
                JsonNode valueIdsNode = child(typeNode, "valueIds");
                if (valuesNode != null && valuesNode.isArray()) {
                    for (int i = 0; i < valuesNode.size(); i++) {
                        String valueName = text(valuesNode.get(i));
                        if (valueName == null) {
                            continue;
                        }
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("name", valueName);
                        value.put("sort", i);
                        if (valueIdsNode != null && valueIdsNode.isArray() && i < valueIdsNode.size()) {
                            Integer vid = integer(valueIdsNode.get(i));
                            if (vid != null) {
                                value.put("vid", vid);
                            }
                        }
                        String image = findImageForSpecValue(skuArray, propName, valueName);
                        if (image != null) {
                            value.put("image", image);
                        }
                        values.add(value);
                    }
                }
                prop.put("values", values);
                props.add(prop);
            }
        }

        LinkedHashMap<String, Map<String, Object>> skuMap = new LinkedHashMap<>();
        if (skuArray != null && skuArray.isArray()) {
            for (JsonNode skuNode : skuArray) {
                String skuName = buildSkuName(skuNode);
                if (skuName == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                String skuId = text(child(skuNode, "skuId"));
                if (skuId != null) {
                    row.put("skuId", skuId);
                }
                Integer stock = extractStock(skuNode);
                if (stock != null) {
                    row.put("stock", stock);
                }
                BigDecimal price = extractPrice(skuNode);
                if (price != null) {
                    row.put("price", price);
                }
                String image = firstNonBlank(text(child(skuNode, "specShowImageUrl")), text(child(skuNode, "thumbUrl")));
                if (image != null) {
                    row.put("image", normalizeImageUrl(image));
                }
                String skuModel = text(child(child(skuNode, "skuExt"), "skuModel"));
                if (skuModel != null) {
                    row.put("skuModel", skuModel);
                }
                skuMap.put(skuName, row);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("props", props);
        out.put("skuMap", skuMap);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("keySep", ">");
        meta.put("source", "window.rawData.store.formatSkuData");
        out.put("meta", meta);
        return out;
    }

    private Map<String, Object> extractSkuData(JsonNode root) {
        JsonNode skuArray = child(storeNode(root), "sku");
        if (skuArray == null || !skuArray.isArray()) {
            return null;
        }

        List<Map<String, Object>> skus = new ArrayList<>();
        Integer totalStock = null;
        for (JsonNode skuNode : skuArray) {
            String skuName = buildSkuName(skuNode);
            if (skuName == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", skuName);
            String skuId = text(child(skuNode, "skuId"));
            if (skuId != null) {
                row.put("skuId", skuId);
            }
            Integer stock = extractStock(skuNode);
            if (stock != null) {
                row.put("stock", stock);
                totalStock = totalStock == null ? stock : totalStock + stock;
            }
            BigDecimal price = extractPrice(skuNode);
            if (price != null) {
                row.put("price", price);
            }
            String image = firstNonBlank(text(child(skuNode, "specShowImageUrl")), text(child(skuNode, "thumbUrl")));
            image = normalizeImageUrl(image);
            if (image != null) {
                row.put("image", image);
            }
            skus.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("skus", skus);
        if (totalStock != null) {
            out.put("totalStock", totalStock);
        }
        return out;
    }

    private Map<String, String> extractAttributes(JsonNode root) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        JsonNode store = storeNode(root);
        JsonNode goodsProperty = child(store, "goodsProperty");
        if (goodsProperty == null || !goodsProperty.isArray()) {
            goodsProperty = child(child(store, "goods"), "goodsProperty");
        }
        if (goodsProperty == null || !goodsProperty.isArray()) {
            return out;
        }

        for (JsonNode item : goodsProperty) {
            String key = text(child(item, "key"));
            if (key == null) {
                continue;
            }
            String value = joinTextArray(child(item, "values"));
            if (value == null) {
                value = firstNonBlank(text(child(item, "value")), text(child(item, "displayValue")));
            }
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    private Map<String, Object> extractCustomMadeSpecs(JsonNode root) {
        JsonNode store = storeNode(root);
        JsonNode specCustom = child(store, "specCustom");
        JsonNode setSpecData = child(child(child(store, "moduleMap"), "setSpecModule"), "data");

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (specCustom != null && isMeaningfulNode(specCustom)) {
            out.put("specCustom", objectMapper.convertValue(specCustom, Map.class));
        }
        if (setSpecData != null && isMeaningfulNode(setSpecData)) {
            out.put("setSpecModuleData", objectMapper.convertValue(setSpecData, Map.class));
        }
        return out.isEmpty() ? null : out;
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

    private JsonNode storeNode(JsonNode root) {
        return child(root, "store");
    }

    private JsonNode child(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isMissingNode() ? null : child;
    }

    private String buildSkuName(JsonNode skuNode) {
        JsonNode specs = child(skuNode, "specs");
        if (specs == null || !specs.isArray() || specs.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode spec : specs) {
            String value = text(child(spec, "specValue"));
            if (value != null) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(">", parts);
    }

    private String findImageForSpecValue(JsonNode skuArray, String specKey, String specValue) {
        if (skuArray == null || !skuArray.isArray() || specValue == null) {
            return null;
        }
        for (JsonNode skuNode : skuArray) {
            JsonNode specs = child(skuNode, "specs");
            if (specs == null || !specs.isArray()) {
                continue;
            }
            for (JsonNode spec : specs) {
                String candidateKey = text(child(spec, "specKey"));
                String candidateValue = text(child(spec, "specValue"));
                if (specValue.equals(candidateValue) && (specKey == null || specKey.equals(candidateKey))) {
                    return normalizeImageUrl(firstNonBlank(text(child(skuNode, "specShowImageUrl")), text(child(skuNode, "thumbUrl"))));
                }
            }
        }
        return null;
    }

    private Integer extractStock(JsonNode skuNode) {
        for (String field : List.of("stockQuantity", "stock", "quantity", "canBookCount", "amountOnSale")) {
            Integer value = integer(child(skuNode, field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal extractPrice(JsonNode skuNode) {
        BigDecimal price = null;
        for (String field : List.of("salePrice", "normalPrice", "discountPrice", "price", "skuPrice")) {
            price = money(child(skuNode, field));
            if (price != null) {
                return price;
            }
        }
        JsonNode skuExt = child(skuNode, "skuExt");
        for (String field : List.of("salePrice", "price", "normalPrice", "unitMainPrice")) {
            price = money(child(skuExt, field));
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private BigDecimal money(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isIntegralNumber()) {
                return BigDecimal.valueOf(node.longValue()).movePointLeft(2);
            }
            if (node.isNumber()) {
                return node.decimalValue();
            }
            String text = text(node);
            if (text == null) {
                return null;
            }
            String normalized = text.replaceAll("[^0-9.\\-]", "");
            if (normalized.isBlank()) {
                return null;
            }
            if (!normalized.contains(".")) {
                return new BigDecimal(normalized).movePointLeft(2);
            }
            return new BigDecimal(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isIntegralNumber()) {
                return node.intValue();
            }
            if (node.isTextual()) {
                String text = node.asText().trim();
                if (text.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(text.replaceAll("[^0-9\\-]", ""));
            }
            if (node.isNumber()) {
                return node.numberValue().intValue();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private String joinTextArray(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode child : node) {
            String value = text(child);
            if (value != null) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private String normalizeMetricText(String input) {
        if (input == null) {
            return null;
        }
        String value = input.replace('（', '(').replace('）', ')').trim();
        value = value.replaceAll("^[\\s(]+", "");
        value = value.replaceAll("[\\s)]+$", "");
        value = value.replace("已售出", "已售");
        value = value.replace("件", "");
        value = value.trim();
        return value.isBlank() ? null : value;
    }

    private boolean isMeaningfulNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isArray()) {
            return !node.isEmpty();
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isMeaningfulNode(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isTextual()) {
            return text(node) != null;
        }
        return true;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
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
