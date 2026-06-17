package com.tminos.productscene.service.pull.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1688 product detail HTML parser (pure local parsing; no AI).
 *
 * Goal: extract as much structured data as possible from the HTML captured by the browser plugin.
 */
public class Alibaba1688HtmlParser {

    private static final Pattern OFFER_ID_PATTERN_1 = Pattern.compile("offer/(\\d+)\\.html", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFER_ID_PATTERN_2 = Pattern.compile("offerId=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIBABA_TPS_SIZE_PATTERN = Pattern.compile("(?i)-tps-(\\d+)-(\\d+)\\.(?:jpg|jpeg|png|webp|gif)$");
    private static final Pattern TRAILING_IMAGE_SIZE_PATTERN = Pattern.compile("(?i)-(\\d+)-(\\d+)\\.(?:jpg|jpeg|png|webp|gif)$");
    private static final Pattern ALIBABA_THUMB_SUFFIX_PATTERN = Pattern.compile("(?i)(\\.(?:jpg|jpeg|png|webp))_b\\.(?:jpg|jpeg|png|webp)$");

    private final ObjectMapper objectMapper;

    public Alibaba1688HtmlParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedProduct parse(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("HTML content is required");
        }

        Document doc = Jsoup.parse(html);

        String productId = extractOfferId(doc, html);
        if (productId == null || productId.isBlank()) {
            throw new IllegalStateException("Failed to parse offerId from HTML");
        }
        String productUrl = extractCanonicalUrl(doc);
        if ((productUrl == null || productUrl.isBlank()) && productId != null) {
            productUrl = "https://detail.1688.com/offer/" + productId + ".html";
        }

        String productName = textOrNull(first(doc, "#productTitle h1"));
        if (productName == null || productName.isBlank()) {
            productName = safeTitle(doc.title());
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalStateException("Failed to parse product title from HTML");
        }

        if (productUrl == null || productUrl.isBlank()) {
            throw new IllegalStateException("Failed to parse canonical product URL from HTML");
        }

        String companyName = textOrNull(first(doc, "#shopNavigation h1"));
        if (companyName == null || companyName.isBlank()) {
            companyName = attrOrNull(first(doc, "#shopNavigation h1"), "title");
        }

        String shippingLocation = textOrNull(first(doc, "#shippingServices .location"));
        if (shippingLocation == null || shippingLocation.isBlank()) {
            shippingLocation = textOrNull(first(doc, ".module-od-shipping-services .location"));
        }

        ShippingServicesInfo shippingServices = extractShippingServices(doc);
        CategoryInfo categoryInfo = extractCategory(html, doc);

        CarouselMedia media = extractCarouselMedia(doc, html);
        List<String> carouselImages = media.bigImages;
        String productMainImage = carouselImages.isEmpty() ? null : carouselImages.get(0);
        if ((productMainImage == null || productMainImage.isBlank()) && media.thumbImages != null && !media.thumbImages.isEmpty()) {
            productMainImage = media.thumbImages.get(0);
        }

        List<String> detailImages = extractDetailImages(doc, html);

        PackagingInfo packagingInfo = extractPackaging(doc);
        PriceInfo priceInfo = extractPriceAndMoq(doc);

        // SKU model (props + skuMap). Prefer parsing from embedded window.context JSON.
        SkuModel skuModel = extractSkuModelFromWindowContext(html);
        SkuInfo skuInfo = extractSkusFromSkuModelOrDom(doc, skuModel);
        List<Map<String, Object>> customMadeSpecs = extractCustomMadeSpecs(doc);
        Map<String, String> attributes = extractAttributes(doc);

        String monthlySales = extractPanelMetric(doc, "月成交");
        String monthlyConsignment = extractPanelMetric(doc, "月代销");
        String annualSales = extractPanelMetric(doc, "年成交");
        if (annualSales == null) {
            annualSales = extractTradeInfoAnnualSales(doc);
        }

        BigDecimal serviceScore = extractServiceScore(doc);
        BigDecimal ratingScore = extractRatingScore(doc);
        Integer reviewCount = extractReviewCount(doc);
        Integer collectCount = extractCollectCount(doc);
        BigDecimal repeatCustomerRate = extractRepeatCustomerRate(doc);
        BigDecimal onTimeDeliveryRate = extractOnTimeDeliveryRate(doc);
        BigDecimal shopPositiveRate = extractShopPositiveRate(doc);
        Boolean powerSeller = extractPowerSeller(doc);
        String settledYearsText = extractSettledYearsText(doc);
        String mainBusiness = extractMainBusiness(doc);

        Boolean hasSevereInventory = null;
        Integer totalStock = skuInfo.totalStock;
        Integer monthlySalesNum = parseFirstInt(monthlySales);
        if (totalStock != null && monthlySalesNum != null) {
            hasSevereInventory = totalStock > 5000 && monthlySalesNum > 1000;
        }

        ParsedProduct out = new ParsedProduct();
        out.setProductId(productId);
        out.setAlibabaProductId(productId);
        out.setProductUrl(productUrl);
        out.setProductName(productName);
        out.setCompanyName(companyName);
        out.setShippingLocation(shippingLocation);
        out.setOriginalCategory(categoryInfo.originalCategory);
        out.setProductCategory(categoryInfo.productCategory);
        out.setLeafCategoryName(categoryInfo.leafCategoryName);
        out.setLeafCategoryId(categoryInfo.leafCategoryId);
        out.setPostCategoryId(categoryInfo.postCategoryId);
        out.setSecondCategoryId(categoryInfo.secondCategoryId);
        out.setTopCategoryId(categoryInfo.topCategoryId);
        out.setProductMainImage(productMainImage);
        out.setCarouselImagesJson(toJsonOrNull(carouselImages));
        out.setCarouselThumbImagesJson(toJsonOrNull(media.thumbImages));
        out.setCarouselVideoUrl(media.videoUrl);
        out.setDetailImagesJson(toJsonOrNull(detailImages));

        out.setPackagingLength(packagingInfo.length);
        out.setPackagingWidth(packagingInfo.width);
        out.setPackagingHeight(packagingInfo.height);
        out.setPackagingWeight(packagingInfo.weight);
        out.setPackagingDimensions(packagingInfo.dimensions);

        out.setMinPrice(priceInfo.minPrice);
        out.setMaxPrice(priceInfo.maxPrice);
        out.setMoq(priceInfo.moq);
        out.setMoqText(priceInfo.moqText);
        out.setPriceStepsJson(priceInfo.priceStepsJson);

        if (skuModel != null) {
            out.setSkuDataJson(skuInfo.skuDataJson);
            out.setSkuModelJson(skuModel.skuModelJson);
        } else {
            out.setSkuDataJson(skuInfo.skuDataJson);
            out.setSkuModelJson(null);
        }
        out.setAttributesDataJson(attributes.isEmpty() ? null : toJsonOrNull(attributes));
        out.setCustomMadeSpecsJson(customMadeSpecs == null || customMadeSpecs.isEmpty() ? null : toJsonOrNull(customMadeSpecs));

        out.setMonthlySales(monthlySales);
        out.setMonthlyConsignment(monthlyConsignment);
        out.setAnnualSales(annualSales);

        out.setServiceScore(serviceScore);
        out.setRatingScore(ratingScore);
        out.setReviewCount(reviewCount);
        out.setCollectCount(collectCount);
        out.setRepeatCustomerRate(repeatCustomerRate);
        out.setOnTimeDeliveryRate(onTimeDeliveryRate);
        out.setShopPositiveRate(shopPositiveRate);
        out.setPowerSeller(powerSeller);
        out.setSettledYearsText(settledYearsText);
        out.setMainBusiness(mainBusiness);
        out.setHasSevereInventory(hasSevereInventory);

        out.setShippingServicesInfo(shippingServices.moduleText);
        out.setBaseFreight(shippingServices.baseFreight);

        out.setOriginalContent(buildSummaryJson(productId, productUrl, productName, companyName, shippingLocation,
                categoryInfo, priceInfo, skuInfo, attributes, packagingInfo,
                serviceScore, repeatCustomerRate, onTimeDeliveryRate, shopPositiveRate,
                powerSeller, settledYearsText, mainBusiness));
        out.setOriginalHtml(html);

        return out;
    }

    private SkuInfo extractSkusFromSkuModelOrDom(Document doc, SkuModel skuModel) {
        if (skuModel != null && skuModel.skuMap != null && !skuModel.skuMap.isEmpty()) {
            SkuInfo info = new SkuInfo();
            List<Map<String, Object>> skus = new ArrayList<>();
            Integer totalStock = null;

            for (Map.Entry<String, Map<String, Object>> e : skuModel.skuMap.entrySet()) {
                String key = e.getKey();
                Map<String, Object> v = e.getValue();
                if (key == null || key.isBlank() || v == null) continue;

                Map<String, Object> sku = new LinkedHashMap<>();
                sku.put("name", key);
                Object skuId = v.get("skuId");
                if (skuId != null) sku.put("skuId", String.valueOf(skuId));
                Object stock = v.get("stock");
                if (stock instanceof Number) {
                    int s = ((Number) stock).intValue();
                    sku.put("stock", s);
                    totalStock = (totalStock == null) ? s : (totalStock + s);
                }
                Object price = v.get("price");
                if (price != null) sku.put("price", price);
                Object image = v.get("image");
                if (image != null) sku.put("image", image);
                skus.add(sku);
            }

            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("skus", skus);
            info.skus = skus;
            info.totalStock = totalStock;
            info.skuDataJson = toJsonOrNull(wrapper);
            return info;
        }

        return extractSkus(doc);
    }

    private SkuModel extractSkuModelFromWindowContext(String html) {
        String json = extractWindowContextJson(html);
        if (json == null || json.isBlank()) return null;

        // window.context payload is usually JSON-like, but some nested objects can contain
        // unquoted numeric keys (e.g. skuWeight: {5765569: 1.0, ...}) which is valid JS
        // but invalid strict JSON. We only need to parse it with Jackson, so sanitize
        // these numeric keys into quoted strings.
        json = quoteNumericObjectKeys(json);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            if (root == null) return null;

            Object result = root.get("result");
            if (!(result instanceof Map)) return null;
            Object data = ((Map<?, ?>) result).get("data");
            if (!(data instanceof Map)) return null;
            Object rootNode = ((Map<?, ?>) data).get("Root");
            if (!(rootNode instanceof Map)) return null;
            Object fields = ((Map<?, ?>) rootNode).get("fields");
            if (!(fields instanceof Map)) return null;
            Object dataJson = ((Map<?, ?>) fields).get("dataJson");
            if (!(dataJson instanceof Map)) return null;

            BigDecimal fallbackSkuPrice = extractFallbackSkuPriceFromDataJson((Map<?, ?>) dataJson);

            Object skuModelObj = ((Map<?, ?>) dataJson).get("skuModel");
            if (!(skuModelObj instanceof Map)) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> skuModel = (Map<String, Object>) skuModelObj;
            Object skuPropsObj = skuModel.get("skuProps");
            Object skuInfoMapObj = skuModel.get("skuInfoMap");
            if (!(skuPropsObj instanceof List) || !(skuInfoMapObj instanceof Map)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skuProps = (List<Map<String, Object>>) skuPropsObj;
            @SuppressWarnings("unchecked")
            Map<String, Object> skuInfoMap = (Map<String, Object>) skuInfoMapObj;

            List<Map<String, Object>> propsOut = new ArrayList<>();
            Map<String, String> propValueImageMap = new LinkedHashMap<>();
            for (Map<String, Object> p : skuProps) {
                if (p == null) continue;
                String propName = decodeHtmlEntities(asString(p.get("prop")));
                if (propName == null || propName.isBlank()) continue;

                Map<String, Object> prop = new LinkedHashMap<>();
                Object fidObj = p.get("fid");
                if (fidObj instanceof Number) prop.put("fid", ((Number) fidObj).intValue());
                prop.put("name", propName);
                prop.put("sort", propsOut.size());

                List<Map<String, Object>> valuesOut = new ArrayList<>();
                Object valuesObj = p.get("value");
                if (valuesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> values = (List<Map<String, Object>>) valuesObj;
                    int vSort = 0;
                    for (Map<String, Object> v : values) {
                        if (v == null) continue;
                        String vName = decodeHtmlEntities(asString(v.get("name")));
                        if (vName == null || vName.isBlank()) continue;

                        Map<String, Object> val = new LinkedHashMap<>();
                        val.put("name", vName);
                        String img = normalizeUrl(asString(v.get("imageUrl")));
                        if (img != null && !img.isBlank()) val.put("image", img);
                        if (img != null && !img.isBlank()) propValueImageMap.put(vName, img);
                        val.put("sort", vSort++);
                        valuesOut.add(val);
                    }
                }
                prop.put("values", valuesOut);
                propsOut.add(prop);
            }

            Map<String, Map<String, Object>> skuMapOut = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : skuInfoMap.entrySet()) {
                String specKey = normalizeSpecKey(e.getKey());
                Object v = e.getValue();
                if (specKey == null || specKey.isBlank() || !(v instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> vm = (Map<String, Object>) v;

                Map<String, Object> sku = new LinkedHashMap<>();
                Object skuId = vm.get("skuId");
                if (skuId != null) sku.put("skuId", String.valueOf(skuId));
                Object stock = firstNonNull(vm.get("canBookCount"), vm.get("stock"), vm.get("quantity"), vm.get("amountOnSale"));
                if (stock instanceof Number) sku.put("stock", ((Number) stock).intValue());
                BigDecimal price = extractSkuPrice(vm);
                if (price == null) price = fallbackSkuPrice;
                if (price != null) sku.put("price", price);
                String image = firstNonBlank(
                        normalizeUrl(asString(vm.get("imageUrl"))),
                        normalizeUrl(asString(vm.get("skuImage"))),
                        normalizeUrl(asString(vm.get("image"))),
                        inferSkuImageFromSpecKey(specKey, propValueImageMap)
                );
                if (image != null) sku.put("image", image);
                skuMapOut.put(specKey, sku);
            }

            List<Map<String, Object>> skusOut = new ArrayList<>();
            Integer totalStock = null;
            for (Map.Entry<String, Map<String, Object>> entry : skuMapOut.entrySet()) {
                Map<String, Object> sku = new LinkedHashMap<>();
                sku.put("name", entry.getKey());
                sku.putAll(entry.getValue());
                skusOut.add(sku);

                Object stock = entry.getValue().get("stock");
                if (stock instanceof Number) {
                    int count = ((Number) stock).intValue();
                    totalStock = totalStock == null ? count : totalStock + count;
                }
            }

            Map<String, Object> skuModelJson = new LinkedHashMap<>();
            skuModelJson.put("props", propsOut);
            skuModelJson.put("skuMap", skuMapOut);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("keySep", ">");
            meta.put("source", "window.context");
            skuModelJson.put("meta", meta);

            Map<String, Object> skuDataJson = new LinkedHashMap<>();
            skuDataJson.put("skus", skusOut);
            if (totalStock != null) skuDataJson.put("totalStock", totalStock);

            SkuModel out = new SkuModel();
            out.skuModelJson = toJsonOrNull(skuModelJson);
            out.skuMap = skuMapOut;
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal extractFallbackSkuPriceFromDataJson(Map<?, ?> dataJson) {
        if (dataJson == null || dataJson.isEmpty()) return null;
        try {
            Object orderParamModel = dataJson.get("orderParamModel");
            if (!(orderParamModel instanceof Map<?, ?> opm)) return null;
            Object orderParam = opm.get("orderParam");
            if (!(orderParam instanceof Map<?, ?> op)) return null;
            Object skuParam = op.get("skuParam");
            if (!(skuParam instanceof Map<?, ?> sp)) return null;
            Object skuRangePrices = sp.get("skuRangePrices");
            if (!(skuRangePrices instanceof List<?> list) || list.isEmpty()) return null;

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                BigDecimal p = parseAnyDecimal(m.get("price"));
                if (p != null) return p;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String quoteNumericObjectKeys(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder out = new StringBuilder(input.length() + 64);
        boolean inStr = false;
        char strCh = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inStr) {
                out.append(c);
                if (c == '\\') {
                    // skip escaped char
                    if (i + 1 < input.length()) {
                        out.append(input.charAt(i + 1));
                        i++;
                    }
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
                out.append(c);
                continue;
            }

            if (c == '{' || c == ',') {
                out.append(c);

                int j = i + 1;
                while (j < input.length()) {
                    char w = input.charAt(j);
                    if (w == ' ' || w == '\n' || w == '\r' || w == '\t') {
                        out.append(w);
                        j++;
                        continue;
                    }
                    break;
                }

                int k = j;
                while (k < input.length()) {
                    char d = input.charAt(k);
                    if (d >= '0' && d <= '9') {
                        k++;
                        continue;
                    }
                    break;
                }

                if (k > j) {
                    int m = k;
                    while (m < input.length()) {
                        char w = input.charAt(m);
                        if (w == ' ' || w == '\n' || w == '\r' || w == '\t') {
                            m++;
                            continue;
                        }
                        break;
                    }
                    if (m < input.length() && input.charAt(m) == ':') {
                        // numeric key detected
                        out.append('"').append(input, j, k).append('"');
                        out.append(input, k, m);
                        out.append(':');
                        i = m;
                        continue;
                    }
                }

                // no replacement; continue
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String decodeHtmlEntities(String value) {
        if (value == null || value.isBlank()) return value;
        return value
                .replace("&gt;", ">")
                .replace("&#62;", ">")
                .replace("&lt;", "<")
                .replace("&#60;", "<")
                .replace("&amp;", "&");
    }

    private String normalizeSpecKey(String specKey) {
        return decodeHtmlEntities(specKey);
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private BigDecimal extractSkuPrice(Map<String, Object> skuInfo) {
        if (skuInfo == null || skuInfo.isEmpty()) return null;

        for (String key : List.of("price", "discountPrice", "skuPrice", "salePrice", "consignPrice", "finalPrice")) {
            BigDecimal direct = parseAnyDecimal(skuInfo.get(key));
            if (direct != null) return direct;
        }

        for (String key : List.of("price", "discountPrice", "skuPrice", "salePrice", "consignPrice", "finalPrice")) {
            Object nested = skuInfo.get(key + "Info");
            if (nested instanceof Map<?, ?> nestedMap) {
                for (Object nestedValue : nestedMap.values()) {
                    BigDecimal direct = parseAnyDecimal(nestedValue);
                    if (direct != null) return direct;
                }
            }
        }

        return null;
    }

    private BigDecimal parseAnyDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(String.valueOf(number));
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                BigDecimal parsed = parseAnyDecimal(nested);
                if (parsed != null) return parsed;
            }
            return null;
        }

        String text = asString(value);
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String inferSkuImageFromSpecKey(String specKey, Map<String, String> propValueImageMap) {
        if (specKey == null || specKey.isBlank() || propValueImageMap == null || propValueImageMap.isEmpty()) return null;
        String[] parts = specKey.split(">");
        for (String part : parts) {
            String image = propValueImageMap.get(part == null ? null : part.trim());
            if (image != null && !image.isBlank()) return image;
        }
        return null;
    }

    private String extractWindowContextJson(String html) {
        if (html == null || html.isBlank()) return null;
        int idx = html.indexOf("window.context=");
        if (idx < 0) return null;

        // 1688 pages typically embed the payload like:
        //   window.context=(function(b,d){...})(window.contextPath,{<payload>})
        // The first '{' after 'window.context=' belongs to the function body, not the payload.
        // Prefer the payload object that comes after '})(window.contextPath,'.
        int braceStart = -1;
        int markerIdx = html.indexOf("})(window.contextPath,", idx);
        if (markerIdx >= 0) {
            braceStart = html.indexOf('{', markerIdx);
        }
        if (braceStart < 0) {
            // Fallback: best-effort (might capture function body on some variants)
            braceStart = html.indexOf('{', idx);
        }
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

    private List<Map<String, Object>> extractCustomMadeSpecs(Document doc) {
        Element root = first(doc, "#customMade");
        if (root == null) return Collections.emptyList();

        Element module = first(root, ".module-od-custom-made");
        if (module == null) module = root;

        List<Map<String, Object>> specs = new ArrayList<>();
        for (Element item : module.select(".custom-service-p-item")) {
            String name = textOrNull(first(item, ".custom-service-pname"));
            if (name == null || name.isBlank()) {
                name = textOrNull(first(item, ".custom-service-txt.custom-service-pname"));
            }
            if (name == null || name.isBlank()) continue;

            List<Map<String, Object>> options = new ArrayList<>();
            for (Element opt : item.select(".v-item")) {
                String optName = textOrNull(first(opt, ".v-name"));
                String optImg = normalizeUrl(pickImgUrl(first(opt, "img")));
                if ((optName == null || optName.isBlank()) && (optImg == null || optImg.isBlank())) {
                    continue;
                }
                Map<String, Object> o = new LinkedHashMap<>();
                if (optName != null && !optName.isBlank()) o.put("name", optName);
                if (optImg != null && !optImg.isBlank()) o.put("image", optImg);
                options.add(o);
            }

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("name", name);
            if (!options.isEmpty()) spec.put("options", options);
            specs.add(spec);
        }

        return specs;
    }

    private ShippingServicesInfo extractShippingServices(Document doc) {
        ShippingServicesInfo out = new ShippingServicesInfo();
        Element el = first(doc, ".module-od-shipping-services.cart-gap");
        if (el == null) el = first(doc, ".module-od-shipping-services");
        if (el == null) return out;

        String text = el.text();
        if (text != null) {
            text = text.replaceAll("\\s+", " ").trim();
        }
        out.moduleText = (text == null || text.isBlank()) ? null : text;

        BigDecimal freight = null;
        if (out.moduleText != null) {
            Matcher m = Pattern.compile("运费\\s*[¥￥]?\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(out.moduleText);
            if (m.find()) {
                freight = parseBigDecimal(m.group(1));
            }
        }
        out.baseFreight = freight;
        return out;
    }

    private String buildSummaryJson(
            String productId,
            String productUrl,
            String productName,
            String companyName,
            String shippingLocation,
            CategoryInfo categoryInfo,
            PriceInfo priceInfo,
            SkuInfo skuInfo,
            Map<String, String> attributes,
            PackagingInfo packagingInfo,
            BigDecimal serviceScore,
            BigDecimal repeatCustomerRate,
            BigDecimal onTimeDeliveryRate,
            BigDecimal shopPositiveRate,
            Boolean powerSeller,
            String settledYearsText,
            String mainBusiness
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("productId", productId);
        summary.put("productUrl", productUrl);
        summary.put("productName", productName);
        summary.put("companyName", companyName);
        summary.put("shippingLocation", shippingLocation);
        summary.put("originalCategory", categoryInfo.originalCategory);
        summary.put("productCategory", categoryInfo.productCategory);
        summary.put("leafCategoryName", categoryInfo.leafCategoryName);
        summary.put("leafCategoryId", categoryInfo.leafCategoryId);
        summary.put("postCategoryId", categoryInfo.postCategoryId);
        summary.put("secondCategoryId", categoryInfo.secondCategoryId);
        summary.put("topCategoryId", categoryInfo.topCategoryId);
        summary.put("minPrice", priceInfo.minPrice);
        summary.put("maxPrice", priceInfo.maxPrice);
        summary.put("moq", priceInfo.moq);
        summary.put("packaging", packagingInfo.asMap());
        summary.put("serviceScore", serviceScore);
        summary.put("repeatCustomerRate", repeatCustomerRate);
        summary.put("onTimeDeliveryRate", onTimeDeliveryRate);
        summary.put("shopPositiveRate", shopPositiveRate);
        summary.put("powerSeller", powerSeller);
        summary.put("settledYearsText", settledYearsText);
        summary.put("mainBusiness", mainBusiness);
        summary.put("skus", skuInfo.skus);
        if (attributes != null && !attributes.isEmpty()) {
            if (attributes.size() > 50) {
                LinkedHashMap<String, String> limited = new LinkedHashMap<>();
                int i = 0;
                for (Map.Entry<String, String> e : attributes.entrySet()) {
                    limited.put(e.getKey(), e.getValue());
                    i++;
                    if (i >= 50) break;
                }
                summary.put("attributes", limited);
            } else {
                summary.put("attributes", attributes);
            }
        }
        return toJsonOrNull(summary);
    }

    private String extractOfferId(Document doc, String rawHtml) {
        String canonical = extractCanonicalUrl(doc);
        String id = matchFirst(canonical, OFFER_ID_PATTERN_1);
        if (id != null) return id;

        for (Element meta : doc.select("meta[name=mobile-agent]")) {
            String c = meta.attr("content");
            id = matchFirst(c, OFFER_ID_PATTERN_1);
            if (id != null) return id;
            id = matchFirst(c, OFFER_ID_PATTERN_2);
            if (id != null) return id;
        }

        id = matchFirst(rawHtml, OFFER_ID_PATTERN_1);
        if (id != null) return id;
        return matchFirst(rawHtml, OFFER_ID_PATTERN_2);
    }

    private String extractCanonicalUrl(Document doc) {
        Element link = first(doc, "link[rel=canonical]");
        return link == null ? null : link.attr("href");
    }

    private List<String> extractCarouselImages(Document doc) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (Element img : doc.select("#gallery img")) {
            String u = normalizeCarouselImageUrl(normalizeUrl(pickImgUrl(img)));
            if (u != null && isImageUrl(u)) {
                urls.add(u);
            }
        }
        return new ArrayList<>(urls);
    }

    private CarouselMedia extractCarouselMedia(Document doc, String rawHtml) {
        CarouselMedia out = new CarouselMedia();

        Element gallery = first(doc, "#gallery");
        if (gallery == null) gallery = first(doc, ".module-od-picture-gallery");
        if (gallery == null) gallery = doc.body();

        Element preview = first(gallery, ".od-gallery-preview");
        if (preview == null) preview = first(gallery, "#od-gallery-preview");

        String video = pickVideoUrl(first(preview, "video"));
        if (video == null) {
            Element source = first(preview, "video source");
            if (source != null) {
                video = normalizeUrl(source.attr("src"));
            }
        }

        List<String> previewDom = extractCarouselPreviewImagesFromDom(gallery);
        List<String> thumbDom = extractCarouselThumbImagesFromDom(gallery);

        List<String> mainImage = rawHtml == null || rawHtml.isBlank()
                ? Collections.emptyList()
                : extractCarouselImagesFromWindowContext(rawHtml, "mainImage");

        List<String> offerAll = rawHtml == null || rawHtml.isBlank()
                ? Collections.emptyList()
                : extractCarouselImagesFromWindowContext(rawHtml, "offerImgList");

        LinkedHashSet<String> big = new LinkedHashSet<>();
        LinkedHashSet<String> thumbs = new LinkedHashSet<>();

        // od-scroller-list-wapper 是缩略图条，不应直接当成轮播大图。
        // 大图优先取 window.context 里的 mainImage / offerImgList，其次取预览区 DOM。
        if (!thumbDom.isEmpty()) {
            thumbs.addAll(thumbDom);
        } else if (!mainImage.isEmpty()) {
            thumbs.addAll(mainImage);
        } else if (!previewDom.isEmpty()) {
            thumbs.addAll(previewDom);
        } else if (!offerAll.isEmpty()) {
            thumbs.addAll(offerAll);
        }

        if (!mainImage.isEmpty()) {
            big.addAll(mainImage);
        } else if (!offerAll.isEmpty()) {
            big.addAll(offerAll);
        } else if (!previewDom.isEmpty()) {
            big.addAll(previewDom);
        } else {
            big.addAll(extractCarouselImages(doc));
        }

        // 若首屏预览图数量更完整，则补齐；但不要覆盖 DOM 轮播顺序。
        if (!previewDom.isEmpty() && big.size() < previewDom.size()) {
            big.clear();
            big.addAll(previewDom);
        }

        if (big.isEmpty() && !thumbs.isEmpty()) {
            List<String> upgradedThumbs = upgradeAlibabaThumbUrls(thumbs);
            if (!upgradedThumbs.isEmpty()) {
                big.addAll(upgradedThumbs);
            } else {
                big.addAll(thumbs);
            }
        }
        if (thumbs.isEmpty() && !big.isEmpty()) {
            thumbs.addAll(big);
        }

        out.bigImages = new ArrayList<>(big);
        out.thumbImages = new ArrayList<>(thumbs);
        out.videoUrl = (video == null || video.isBlank()) ? null : video;
        return out;
    }

    private List<String> upgradeAlibabaThumbUrls(Iterable<String> urls) {
        if (urls == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> upgraded = new LinkedHashSet<>();
        for (String url : urls) {
            String candidate = upgradeAlibabaThumbUrl(url);
            if (candidate != null && isImageUrl(candidate)) {
                upgraded.add(candidate);
            }
        }
        return new ArrayList<>(upgraded);
    }

    private String upgradeAlibabaThumbUrl(String url) {
        String normalized = normalizeCarouselImageUrl(url);
        if (normalized == null) {
            return null;
        }
        String base = stripUrlQueryAndFragment(normalized);
        if (base == null || base.isBlank()) {
            return normalized;
        }
        Matcher matcher = ALIBABA_THUMB_SUFFIX_PATTERN.matcher(base);
        if (!matcher.find()) {
            return normalized;
        }
        String upgradedBase = matcher.replaceFirst("$1");
        if (upgradedBase.equals(base)) {
            return normalized;
        }
        String suffix = normalized.substring(base.length());
        return upgradedBase + suffix;
    }

    private List<String> extractCarouselPreviewImagesFromDom(Element gallery) {
        if (gallery == null) return Collections.emptyList();

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (Element img : gallery.select(".od-gallery-preview img, #od-gallery-preview img")) {
            String u = normalizeCarouselImageUrl(normalizeUrl(pickImgUrl(img)));
            if (u != null && isImageUrl(u)) {
                urls.add(u);
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> extractCarouselThumbImagesFromDom(Element gallery) {
        if (gallery == null) return Collections.emptyList();

        LinkedHashSet<String> urls = new LinkedHashSet<>();

        Element wrapper = first(gallery, ".od-scroller-list-wapper");
        if (wrapper == null) wrapper = first(gallery, "#od-scroller-list-wapper");
        if (wrapper == null) wrapper = gallery;

        for (Element cover : wrapper.select(".od-scroller-item .v-image-cover")) {
            String u = extractBackgroundImageUrl(cover);
            u = normalizeCarouselImageUrl(normalizeUrl(u));
            if (u != null && isImageUrl(u)) {
                urls.add(u);
            }
        }

        if (urls.isEmpty()) {
            for (Element img : wrapper.select(".od-scroller-item img")) {
                String u = normalizeCarouselImageUrl(normalizeUrl(pickImgUrl(img)));
                if (u != null && isImageUrl(u)) {
                    urls.add(u);
                }
            }
        }

        // 再做一层老结构兜底，避免个别页面 wrapper 缺失
        if (urls.isEmpty()) {
            for (Element cover : gallery.select(".img-switch-item .v-image-cover, .img-list-wrapper .v-image-cover")) {
                String u = extractBackgroundImageUrl(cover);
                u = normalizeCarouselImageUrl(normalizeUrl(u));
                if (u != null && isImageUrl(u)) {
                    urls.add(u);
                }
            }
        }

        if (urls.isEmpty()) {
            for (Element img : gallery.select(".img-list-wrapper img, #img-list-wrapper img, .od-gallery-scroller img")) {
                String u = normalizeCarouselImageUrl(normalizeUrl(pickImgUrl(img)));
                if (u != null && isImageUrl(u)) {
                    urls.add(u);
                }
            }
        }

        return new ArrayList<>(urls);
    }

    private String normalizeCarouselImageUrl(String u) {
        if (u == null) return null;
        String s = u.trim();
        if (s.isBlank()) return null;

        if (s.startsWith("\\\"")) s = s.substring(2);
        if (s.startsWith("\"") || s.startsWith("'")) s = s.substring(1);
        if (s.endsWith("\"") || s.endsWith("'")) s = s.substring(0, s.length() - 1);
        s = s.trim();

        if (!isCleanHttpImageUrlCandidate(s)) return null;

        String base = stripUrlQueryAndFragment(s);
        if (!isRecognizableImageUrlBase(base)) {
            return null;
        }
        return s;
    }

    private String extractBackgroundImageUrl(Element el) {
        if (el == null) return null;

        String style = firstNonBlank(el.attr("style"), attrOrNull(el, "style"));
        if (style == null || style.isBlank()) return null;

        Matcher m = Pattern.compile("url\\((['\"]?)(.*?)\\1\\)", Pattern.CASE_INSENSITIVE).matcher(style);
        if (m.find()) {
            String u = m.group(2);
            return (u == null || u.isBlank()) ? null : u.trim();
        }
        return null;
    }

    private List<String> extractCarouselImagesFromWindowContext(String html, String fieldName) {
        if (html == null || html.isBlank() || fieldName == null || fieldName.isBlank()) {
            return Collections.emptyList();
        }

        try {
            String json = extractWindowContextJson(html);
            if (json == null || json.isBlank()) return Collections.emptyList();
            json = quoteNumericObjectKeys(json);

            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            if (root == null) return Collections.emptyList();

            Object result = root.get("result");
            if (!(result instanceof Map<?, ?> r)) return Collections.emptyList();

            Object node = deepGet(r, "data", "gallery", "fields", fieldName);
            if (node == null) return Collections.emptyList();

            LinkedHashSet<String> urls = new LinkedHashSet<>();
            collectGalleryImageUrls(node, urls, 0);
            return new ArrayList<>(urls);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private void collectGalleryImageUrls(Object node, Set<String> out, int depth) {
        if (node == null || out == null || depth > 8) return;

        if (node instanceof String s) {
            String u = normalizeImageUrl(normalizeUrl(s));
            if (u != null && isImageUrl(u)) {
                out.add(u);
            }
            return;
        }

        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);

                if (value instanceof String s) {
                    if (key.contains("image") || key.contains("img") || key.contains("url") || key.contains("src") || key.contains("original")) {
                        String u = normalizeImageUrl(normalizeUrl(s));
                        if (u != null && isImageUrl(u)) {
                            out.add(u);
                        }
                    }
                } else {
                    collectGalleryImageUrls(value, out, depth + 1);
                }
            }
            return;
        }

        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectGalleryImageUrls(item, out, depth + 1);
            }
        }
    }

    private List<String> extractDetailImages(Document doc, String rawHtml) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        // 先按原始 HTML 字符串做严格切块：
        // 只取 <div id="detail"> 到 desc-lazyload-container / desc-lazyload / price-explain 之前的图片。
        // 这样即使页面 DOM 因模板闭合异常被 Jsoup 自动重排，也不会把延迟容器里的图带进来。
        if (rawHtml != null && !rawHtml.isBlank()) {
            List<String> strict = extractDetailImagesFromRawDetailBlock(rawHtml);
            if (strict != null && !strict.isEmpty()) {
                return finalizeDetailImageUrls(strict, rawHtml);
            }
        }

        Element detailRoot = first(doc, "#detail");
        if (detailRoot != null) {
            collectImagesFromDetailRoot(detailRoot, urls);
            return finalizeDetailImageUrls(new ArrayList<>(urls), rawHtml);
        }

        collectImagesFromContainer(first(doc, "#description"), urls);
        collectImagesFromContainer(first(doc, ".html-description"), urls);

        if (urls.isEmpty()) {
            for (Element img : doc.select(".desc-img-container img, .detail-content img, .detail-desc img")) {
                if (isInsideExcludedDetailContainer(img)) continue;
                String u = normalizeImageUrl(normalizeUrl(pickImgUrl(img)));
                if (u != null && isImageUrl(u)) {
                    urls.add(u);
                }
            }
        }

        if (urls.isEmpty() && rawHtml != null && !rawHtml.isBlank()) {
            String detailUrl = extractDetailUrlFromWindowContext(rawHtml);
            if (detailUrl != null && !detailUrl.isBlank()) {
                urls.addAll(extractDetailImagesFromItemCdn(detailUrl));
            }
        }

        if (urls.isEmpty() && rawHtml != null && !rawHtml.isBlank()) {
            urls.addAll(extractDetailImagesFromWindowContext(rawHtml));
        }

        if (urls.isEmpty() && rawHtml != null && !rawHtml.isBlank()) {
            urls.addAll(extractDetailImagesFromRawHtml(rawHtml));
        }

        return finalizeDetailImageUrls(new ArrayList<>(urls), rawHtml);
    }

    private List<String> finalizeDetailImageUrls(List<String> urls, String rawHtml) {
        List<String> originalUrls = urls == null ? Collections.emptyList() : new ArrayList<>(urls);
        List<String> processedUrls = postProcessDetailImageUrls(originalUrls);
        if (!shouldPreferItemCdnDetailImages(originalUrls, processedUrls, rawHtml)) {
            return processedUrls;
        }

        String detailUrl = extractDetailUrlFromWindowContext(rawHtml);
        if (detailUrl == null || detailUrl.isBlank()) {
            return processedUrls;
        }

        List<String> itemCdnUrls = extractDetailImagesFromItemCdn(detailUrl);
        return itemCdnUrls.isEmpty() ? processedUrls : itemCdnUrls;
    }

    private boolean shouldPreferItemCdnDetailImages(List<String> originalUrls, List<String> processedUrls, String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return false;
        String detailUrl = extractDetailUrlFromWindowContext(rawHtml);
        if (detailUrl == null || detailUrl.isBlank()) return false;
        if (originalUrls == null || originalUrls.isEmpty()) return false;

        for (String url : originalUrls) {
            if (isLikelyDecorativeDetailImage(url)) {
                return true;
            }
        }
        return processedUrls != null && processedUrls.size() > 12;
    }

    private void collectImagesFromDetailRoot(Element detailRoot, Set<String> out) {
        if (detailRoot == null || out == null) return;

        Element clone = detailRoot.clone();
        removeExcludedDetailNodes(clone);

        collectImages(clone.select("img"), out);
        if (!out.isEmpty()) return;

        String[] fragments = new String[]{clone.text(), clone.data(), clone.html()};
        for (String frag : fragments) {
            if (frag == null) continue;
            String s = frag.trim();
            if (s.isEmpty()) continue;
            if (!s.contains("<img") && !s.contains("IMG")) continue;
            Document sub = Jsoup.parseBodyFragment(s);
            removeExcludedDetailNodes(sub);
            collectImages(sub.select("img"), out);
            if (!out.isEmpty()) return;
        }
    }

    private List<String> extractDetailImagesFromRawDetailBlock(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return Collections.emptyList();

        int start = indexOfIgnoreCase(rawHtml, "<div id=\"detail\"");
        if (start < 0) start = indexOfIgnoreCase(rawHtml, "<div id='detail'");
        if (start < 0) return Collections.emptyList();

        int end = rawHtml.length();
        String[] endMarkers = new String[]{
                "<div id=\"detail-notice-container-bottom\"",
                "<div id='detail-notice-container-bottom'",
                "<div id=\"desc-lazyload-container\"",
                "<div id='desc-lazyload-container'",
                "<div id=\"desc-lazyload\"",
                "<div id='desc-lazyload'",
                "<div class=\"price-explain\"",
                "<div class='price-explain'",
                "</template>"
        };
        for (String marker : endMarkers) {
            int idx = indexOfIgnoreCase(rawHtml, marker, start + 1);
            if (idx >= 0 && idx < end) {
                end = idx;
            }
        }

        if (end <= start) return Collections.emptyList();

        String block = rawHtml.substring(start, end);
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        Document sub = Jsoup.parseBodyFragment(block);
        removeExcludedDetailNodes(sub);
        collectImages(sub.select("img"), urls);
        if (urls.isEmpty()) {
            collectImageUrlsFromText(block, urls);
        }

        return new ArrayList<>(urls);
    }


    private boolean isInsideExcludedDetailContainer(Element el) {
        if (el == null) return false;
        return el.closest("#desc-lazyload-container") != null
                || el.closest("#desc-lazyload") != null
                || el.closest(".sdmap-dynamic-offer-list") != null
                || el.closest(".offer-list-wapper") != null
                || el.closest(".desc-dynamic-module") != null;
    }

    private List<String> extractDetailImagesFromWindowContext(String html) {
        if (html == null || html.isBlank()) return Collections.emptyList();

        try {
            String json = extractWindowContextJson(html);
            if (json == null || json.isBlank()) return Collections.emptyList();
            json = quoteNumericObjectKeys(json);

            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            if (root == null) return Collections.emptyList();

            Object result = root.get("result");
            if (!(result instanceof Map<?, ?> r)) return Collections.emptyList();

            Object descNode = deepGet(r, "data", "description", "fields");
            if (descNode == null) {
                descNode = deepGet(r, "global", "globalData", "model", "offerDetail");
            }
            if (descNode == null) return Collections.emptyList();

            LinkedHashSet<String> urls = new LinkedHashSet<>();
            collectDescriptionImageUrls(descNode, urls, 0);
            return postProcessDetailImageUrls(new ArrayList<>(urls));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String extractDetailUrlFromWindowContext(String html) {
        if (html == null || html.isBlank()) return null;
        try {
            String json = extractWindowContextJson(html);
            if (json == null || json.isBlank()) return null;
            json = quoteNumericObjectKeys(json);

            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            if (root == null) return null;

            Object result = root.get("result");
            if (!(result instanceof Map<?, ?> r)) return null;

            String u = asString(deepGet(r, "data", "description", "fields", "detailUrl"));
            if (u == null || u.isBlank()) {
                u = asString(deepGet(r, "global", "globalData", "model", "offerDetail", "detailUrl"));
            }
            if (u == null || u.isBlank()) return null;
            u = normalizeUrl(u);
            return u;
        } catch (Exception ignored) {
            return null;
        }
    }

    protected List<String> extractDetailImagesFromItemCdn(String detailUrl) {
        if (detailUrl == null || detailUrl.isBlank()) return Collections.emptyList();

        try {
            String html = org.jsoup.Jsoup.connect(detailUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            if (html == null || html.isBlank()) return Collections.emptyList();

            LinkedHashSet<String> urls = new LinkedHashSet<>();

            Document doc = org.jsoup.Jsoup.parse(html);
            removeExcludedDetailNodes(doc);
            collectImages(doc.select("img"), urls);

            collectImageUrlsFromText(html, urls);

            return postProcessDetailImageUrls(new ArrayList<>(urls));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Object deepGet(Map<?, ?> root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(k);
        }
        return cur;
    }

    private void collectDescriptionImageUrls(Object node, Set<String> out, int depth) {
        if (node == null || out == null || depth > 10) return;

        if (node instanceof String s) {
            collectImageUrlsFromText(s, out);
            String u = normalizeImageUrl(normalizeUrl(s));
            if (u != null && isImageUrl(u)) {
                out.add(u);
            }
            return;
        }

        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                Object value = entry.getValue();

                if (value instanceof String s) {
                    if (key.contains("content") || key.contains("html") || key.contains("desc") || key.contains("detail")) {
                        collectImageUrlsFromText(s, out);
                    }

                    if (!key.contains("detailurl")) {
                        String u = normalizeImageUrl(normalizeUrl(s));
                        if (u != null && isImageUrl(u)) {
                            out.add(u);
                        }
                    }
                } else {
                    collectDescriptionImageUrls(value, out, depth + 1);
                }
            }
            return;
        }

        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectDescriptionImageUrls(item, out, depth + 1);
            }
        }
    }

    private void collectImageUrlsFromText(String text, Set<String> out) {
        if (text == null || text.isBlank() || out == null) return;

        String s = text
                .replace("\\/", "/")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&amp;", "&");

        Pattern p = Pattern.compile("((?:https?:)?//[^\\s\"'<>\\\\]+?\\.(?:jpg|jpeg|png|webp|gif)(?:\\?[^\\s\"'<>]*)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(s);
        while (m.find()) {
            String u = normalizeImageUrl(normalizeUrl(m.group(1)));
            if (u != null && isImageUrl(u)) {
                out.add(u);
            }
        }
    }

    private List<String> extractDetailImagesFromRawHtml(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return Collections.emptyList();
        String scope = extractDetailScope(rawHtml);
        if (scope == null || scope.isBlank()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> out = new LinkedHashSet<>();
        Document sub = Jsoup.parseBodyFragment(scope);
        removeExcludedDetailNodes(sub);
        collectImages(sub.select("img"), out);
        if (out.isEmpty()) {
            Pattern p = Pattern.compile("<img\\b[^>]*?(?:src|data-src|data-lazy-src|data-original|data-ks-lazyload|data-lazyload|data-lazyload-src|srcset)\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(sub.html());
            while (m.find()) {
                String u = normalizeImageUrl(normalizeUrl(m.group(2)));
                if (u != null && isImageUrl(u)) {
                    out.add(u);
                }
            }
        }
        return postProcessDetailImageUrls(new ArrayList<>(out));
    }

    private String extractDetailScope(String html) {
        if (html == null || html.isBlank()) return null;

        Matcher dm = Pattern.compile("id\\s*=\\s*(['\"])detail\\1", Pattern.CASE_INSENSITIVE).matcher(html);
        if (dm.find()) {
            int detailIdx = dm.start();
            int endIdx = indexOfIgnoreCase(html, "id=\"detail-notice-container-bottom\"", detailIdx);
            if (endIdx < 0) endIdx = indexOfIgnoreCase(html, "id='detail-notice-container-bottom'", detailIdx);
            if (endIdx < 0) endIdx = indexOfIgnoreCase(html, "</template>", detailIdx);
            if (endIdx < 0) endIdx = Math.min(html.length(), detailIdx + 250_000);
            return html.substring(detailIdx, endIdx);
        }

        int vIdx = indexOfIgnoreCase(html, "<v-detail-7");
        if (vIdx >= 0) {
            int endIdx = indexOfIgnoreCase(html, "</template>", vIdx);
            if (endIdx < 0) endIdx = indexOfIgnoreCase(html, "</v-detail-7>", vIdx);
            if (endIdx < 0) endIdx = Math.min(html.length(), vIdx + 250_000);
            return html.substring(vIdx, endIdx);
        }

        return null;
    }

    private List<String> postProcessDetailImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String u : urls) {
            if (u == null || u.isBlank()) continue;

            String s = normalizeImageUrl(normalizeUrl(u));
            if (s == null || !isImageUrl(s)) continue;
            if (isLikelyDecorativeDetailImage(s)) continue;

            out.add(s);
        }
        return new ArrayList<>(out);
    }

    private void removeExcludedDetailNodes(Element root) {
        if (root == null) {
            return;
        }
        root.select(
                "#desc-lazyload-container, #desc-lazyload, " +
                        ".sdmap-dynamic-offer-list, .offer-list-wapper, .desc-dynamic-module"
        ).remove();
    }

    private boolean isLikelyDecorativeDetailImage(String url) {
        if (url == null || url.isBlank()) return false;

        String lower = stripUrlQueryAndFragment(url);
        if (lower == null || lower.isBlank()) return false;
        lower = lower.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("-2-gg_dtc")) return true;

        boolean publicAlibabaAsset = lower.contains("img.alicdn.com/")
                || lower.contains("gw.alicdn.com/")
                || lower.contains("alicdn.com/tfs/");
        if (!publicAlibabaAsset) {
            return false;
        }

        int[] imageSize = extractTrailingImageSize(lower);
        if (imageSize == null) {
            return false;
        }

        int width = imageSize[0];
        int height = imageSize[1];
        if (width <= 0 || height <= 0) {
            return false;
        }
        int min = Math.min(width, height);
        int max = Math.max(width, height);
        long area = (long) width * height;

        if (width <= 200 && height <= 200) return true;
        if (min <= 96 && max <= 400) return true;
        return area <= 45_000L;
    }

    private int[] extractTrailingImageSize(String lowerUrl) {
        if (lowerUrl == null || lowerUrl.isBlank()) return null;

        Matcher tpsMatcher = ALIBABA_TPS_SIZE_PATTERN.matcher(lowerUrl);
        if (tpsMatcher.find()) {
            return new int[]{parsePositiveInt(tpsMatcher.group(1)), parsePositiveInt(tpsMatcher.group(2))};
        }

        Matcher trailingMatcher = TRAILING_IMAGE_SIZE_PATTERN.matcher(lowerUrl);
        if (trailingMatcher.find()) {
            return new int[]{parsePositiveInt(trailingMatcher.group(1)), parsePositiveInt(trailingMatcher.group(2))};
        }

        return null;
    }

    private int parsePositiveInt(String value) {
        if (value == null || value.isBlank()) return -1;
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String normalizeImageUrl(String u) {
        if (u == null) return null;
        String s = u.trim();
        if (s.isBlank()) return null;

        // Some itemcdn description HTML contains escaped quotes inside attributes, e.g. \"https://...jpg
        // Strip wrapping/escaped quotes before normalizing.
        if (s.startsWith("\\\"")) s = s.substring(2);
        if (s.startsWith("\"") || s.startsWith("'")) s = s.substring(1);
        if (s.endsWith("\"") || s.endsWith("'")) s = s.substring(0, s.length() - 1);
        s = s.trim();

        if (!isCleanHttpImageUrlCandidate(s)) return null;

        String base = stripUrlQueryAndFragment(s);
        if (!isRecognizableImageUrlBase(base)) return null;
        return s;
    }

    private boolean isCleanHttpImageUrlCandidate(String value) {
        if (value == null || value.isBlank()) return false;

        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("[pasted")) return false;
        if (value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return false;
        }
        if (value.indexOf('"') >= 0 || value.indexOf('\\') >= 0 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            return false;
        }

        int firstHttp = lower.indexOf("http");
        int nextHttp = lower.indexOf("http", firstHttp + 4);
        return nextHttp < 0;
    }

    private String stripUrlQueryAndFragment(String value) {
        if (value == null || value.isBlank()) return value;
        String base = value;
        int q = base.indexOf('?');
        if (q > 0) {
            base = base.substring(0, q);
        }
        int h = base.indexOf('#');
        if (h > 0) {
            base = base.substring(0, h);
        }
        return base;
    }

    private boolean isRecognizableImageUrlBase(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        int end = firstImageExtEndIndex(lower);
        if (end < 0) return false;
        if (end == value.length()) {
            return true;
        }
        return lower.matches(".*\\.(?:jpg|jpeg|png|webp|gif)(?:[_.][^/?#]*)?\\.(?:jpg|jpeg|png|webp|gif)$");
    }

    private int firstImageExtEndIndex(String lowerUrl) {
        if (lowerUrl == null) return -1;
        int jpg = lowerUrl.indexOf(".jpg");
        int jpeg = lowerUrl.indexOf(".jpeg");
        int png = lowerUrl.indexOf(".png");
        int webp = lowerUrl.indexOf(".webp");
        int gif = lowerUrl.indexOf(".gif");

        int best = Integer.MAX_VALUE;
        int len = -1;
        if (jpg >= 0 && jpg < best) {
            best = jpg;
            len = 4;
        }
        if (jpeg >= 0 && jpeg < best) {
            best = jpeg;
            len = 5;
        }
        if (png >= 0 && png < best) {
            best = png;
            len = 4;
        }
        if (webp >= 0 && webp < best) {
            best = webp;
            len = 5;
        }
        if (gif >= 0 && gif < best) {
            best = gif;
            len = 4;
        }
        if (best == Integer.MAX_VALUE) return -1;
        return best + len;
    }

    private int indexOfIgnoreCase(String s, String needle) {
        return indexOfIgnoreCase(s, needle, 0);
    }

    private int indexOfIgnoreCase(String s, String needle, int fromIndex) {
        if (s == null || needle == null) return -1;
        if (fromIndex < 0) fromIndex = 0;
        String hay = s.toLowerCase(Locale.ROOT);
        String nd = needle.toLowerCase(Locale.ROOT);
        return hay.indexOf(nd, fromIndex);
    }

    private void collectImages(Elements imgs, Set<String> out) {
        if (imgs == null || imgs.isEmpty() || out == null) return;
        for (Element img : imgs) {
            if (isInsideExcludedDetailContainer(img)) continue;
            String u = normalizeImageUrl(normalizeUrl(pickImgUrl(img)));
            if (u != null && isImageUrl(u)) {
                out.add(u);
            }
        }
    }

    private void collectImagesFromContainer(Element container, Set<String> out) {
        if (container == null || out == null) return;

        collectImages(container.select("img"), out);
        if (!out.isEmpty()) return;

        String[] fragments = new String[]{container.text(), container.data(), container.html()};
        for (String frag : fragments) {
            if (frag == null) continue;
            String s = frag.trim();
            if (s.isEmpty()) continue;
            if (!s.contains("<img") && !s.contains("IMG")) continue;
            Document sub = Jsoup.parseBodyFragment(s);
            removeExcludedDetailNodes(sub);
            collectImages(sub.select("img"), out);
            if (!out.isEmpty()) return;
        }
    }

    private PackagingInfo extractPackaging(Document doc) {
        PackagingInfo info = new PackagingInfo();
        Element table = first(doc, "#productPackInfo table");
        if (table == null) {
            table = first(doc, ".offer-pack-info-list table");
        }
        if (table == null) return info;

        Element row = first(table, "tbody tr");
        if (row == null) {
            return info;
        }

        Elements tds = row.select("td");
        if (tds.size() >= 6) {
            info.length = parseBigDecimal(tds.get(1).text());
            info.width = parseBigDecimal(tds.get(2).text());
            info.height = parseBigDecimal(tds.get(3).text());
            info.weight = parseBigDecimal(tds.get(5).text());
            if (info.length != null && info.width != null && info.height != null) {
                info.dimensions = info.length.stripTrailingZeros().toPlainString() + "×" +
                        info.width.stripTrailingZeros().toPlainString() + "×" +
                        info.height.stripTrailingZeros().toPlainString();
            }
            return info;
        }

        Elements headers = table.select("thead th");
        if (!headers.isEmpty() && tds.size() == headers.size()) {
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).text();
                String v = tds.get(i).text();
                if (h == null) h = "";
                if (h.contains("重量")) {
                    info.weight = parseWeightByHeader(h, v);
                }
            }
        }
        return info;
    }

    private BigDecimal parseWeightByHeader(String header, String value) {
        BigDecimal num = parseBigDecimal(value);
        if (num == null) return null;
        String h = header == null ? "" : header.toLowerCase(Locale.ROOT);
        if (h.contains("kg") || h.contains("千克") || h.contains("公斤")) {
            try {
                return num.multiply(new BigDecimal("1000"));
            } catch (Exception e) {
                return null;
            }
        }
        return num;
    }

    private PriceInfo extractPriceAndMoq(Document doc) {
        PriceInfo info = new PriceInfo();

        List<Map<String, Object>> steps = extractStepPrices(doc);
        if (steps != null && !steps.isEmpty()) {
            info.priceStepsJson = toJsonOrNull(steps);

            BigDecimal min = null;
            BigDecimal max = null;
            String moqText = null;
            Integer moq = null;

            for (Map<String, Object> step : steps) {
                Object p = step.get("price");
                if (p instanceof BigDecimal bd) {
                    if (min == null || bd.compareTo(min) < 0) min = bd;
                    if (max == null || bd.compareTo(max) > 0) max = bd;
                }
                if (moqText == null) {
                    Object range = step.get("range");
                    if (range instanceof String s && s.contains("起批")) {
                        moqText = s;
                        moq = parseFirstInt(s);
                    }
                }
            }

            info.minPrice = min;
            info.maxPrice = (max != null ? max : min);
            info.moqText = moqText;
            info.moq = moq;
            return info;
        }

        BigDecimal mainPrice = parseMoney(textOrNull(first(doc, "#mainPrice .price-info")));
        if (mainPrice == null) {
            mainPrice = parseMoney(textOrNull(first(doc, "#mainPrice")));
        }
        info.minPrice = mainPrice;
        info.maxPrice = mainPrice;

        String moqText = textOrNull(first(doc, "#mainPrice"));
        Integer moq = null;
        if (moqText != null) {
            Matcher m = Pattern.compile("(\\d+)\\s*[^\\s]*起批").matcher(moqText);
            if (m.find()) moq = parseFirstInt(m.group(1));
        }
        info.moq = moq;
        info.moqText = moqText;
        return info;
    }

    private List<Map<String, Object>> extractStepPrices(Document doc) {
        Element module = first(doc, ".module-od-main-price.cart-gap");
        if (module == null) module = first(doc, ".module-od-main-price");
        if (module == null) return Collections.emptyList();

        Element step = first(module, ".step-price");
        if (step == null) return Collections.emptyList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Element comp : step.select(".price-comp")) {
            String priceText = textOrNull(first(comp, ".price-info"));
            BigDecimal price = parseMoney(priceText);

            String range = textOrNull(first(comp, "p"));
            if (range == null) range = textOrNull(first(comp, "p span"));

            if (price == null && (range == null || range.isBlank())) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            if (price != null) m.put("price", price);
            if (range != null && !range.isBlank()) m.put("range", range);
            out.add(m);
        }
        return out;
    }

    private SkuInfo extractSkus(Document doc) {
        SkuInfo info = new SkuInfo();
        List<Map<String, Object>> skus = new ArrayList<>();
        Integer totalStock = null;

        for (Element item : doc.select("#skuSelection .expand-view-item")) {
            String name = textOrNull(first(item, ".item-label"));
            String priceText = textOrNull(first(item, ".item-price-stock"));
            String stockText = null;
            Elements ps = item.select(".item-price-stock");
            if (ps.size() >= 2) {
                stockText = ps.get(1).text();
            }

            BigDecimal price = parseMoney(priceText);
            Integer stock = parseFirstInt(stockText);
            String image = pickImgUrl(first(item, ".item-image-icon img"));

            Map<String, Object> sku = new LinkedHashMap<>();
            sku.put("name", name);
            if (price != null) sku.put("price", price);
            if (stock != null) sku.put("stock", stock);
            if (image != null) sku.put("image", image);
            skus.add(sku);

            if (stock != null) {
                totalStock = (totalStock == null) ? stock : (totalStock + stock);
            }
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("skus", skus);

        info.skus = skus;
        info.totalStock = totalStock;
        info.skuDataJson = toJsonOrNull(wrapper);
        return info;
    }

    private Map<String, String> extractAttributes(Document doc) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        Element table = first(doc, "#productAttributes table");
        if (table == null) {
            return map;
        }

        for (Element tr : table.select("tr")) {
            Elements ths = tr.select("th");
            Elements tds = tr.select("td");
            int pairs = Math.min(ths.size(), tds.size());
            for (int i = 0; i < pairs; i++) {
                String k = ths.get(i).text();
                String v = tds.get(i).text();
                if (k != null) k = k.trim();
                if (v != null) v = v.trim();
                if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                    map.put(k, v);
                }
            }
        }
        return map;
    }

    private CategoryInfo extractCategory(String html, Document doc) {
        CategoryInfo merged = new CategoryInfo();
        applyCategoryInfo(merged, extractCategoryFromWindowContext(html));
        applyCategoryInfo(merged, extractCategory(doc));

        if (merged.leafCategoryName == null) {
            merged.leafCategoryName = firstNonBlank(merged.productCategory, merged.originalCategory);
        }
        if (merged.productCategory == null) {
            merged.productCategory = merged.leafCategoryName;
        }
        if (merged.originalCategory == null) {
            merged.originalCategory = firstNonBlank(merged.productCategory, merged.leafCategoryName);
        }
        if (merged.leafCategoryId == null) {
            merged.leafCategoryId = merged.postCategoryId;
        }
        if (merged.postCategoryId == null) {
            merged.postCategoryId = merged.leafCategoryId;
        }
        return merged;
    }

    private void applyCategoryInfo(CategoryInfo target, CategoryInfo source) {
        if (target == null || source == null) {
            return;
        }
        target.productCategory = firstNonBlank(target.productCategory, normalizeCategoryText(source.productCategory));
        target.originalCategory = firstNonBlank(target.originalCategory, normalizeCategoryText(source.originalCategory));
        target.leafCategoryName = firstNonBlank(target.leafCategoryName, normalizeCategoryText(source.leafCategoryName));
        target.leafCategoryId = firstNonBlank(target.leafCategoryId, trimToNull(source.leafCategoryId));
        target.postCategoryId = firstNonBlank(target.postCategoryId, trimToNull(source.postCategoryId));
        target.secondCategoryId = firstNonBlank(target.secondCategoryId, trimToNull(source.secondCategoryId));
        target.topCategoryId = firstNonBlank(target.topCategoryId, trimToNull(source.topCategoryId));
    }

    private CategoryInfo extractCategoryFromWindowContext(String html) {
        String json = extractWindowContextJson(html);
        if (json == null || json.isBlank()) {
            return null;
        }

        json = quoteNumericObjectKeys(json);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            if (root == null || root.isEmpty()) {
                return null;
            }

            CategoryInfo info = new CategoryInfo();
            info.leafCategoryName = normalizeCategoryText(findFirstStringByKeys(root,
                    "leafCategoryName", "leafCategoryText", "categoryName", "postCategoryName"));
            info.leafCategoryId = findFirstIdByKeys(root, "leafCategoryId");
            info.postCategoryId = findFirstIdByKeys(root, "postCategoryId");
            info.secondCategoryId = findFirstIdByKeys(root, "secondCategoryId");
            info.topCategoryId = findFirstIdByKeys(root, "topCategoryId");

            String fullCategoryPath = normalizeCategoryText(findFirstStringByKeys(root,
                    "fullCategoryName", "categoryPath", "categoryFullPath", "categoryPathName"));
            info.productCategory = firstNonBlank(info.leafCategoryName, fullCategoryPath);
            info.originalCategory = firstNonBlank(fullCategoryPath, info.leafCategoryName);

            if (info.productCategory == null
                    && info.originalCategory == null
                    && info.leafCategoryId == null
                    && info.postCategoryId == null
                    && info.secondCategoryId == null
                    && info.topCategoryId == null) {
                return null;
            }
            return info;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String findFirstStringByKeys(Object root, String... keys) {
        Object value = findFirstValueByKeys(root, Set.of(keys));
        return normalizeCategoryText(asString(value));
    }

    private String findFirstIdByKeys(Object root, String... keys) {
        Object value = findFirstValueByKeys(root, Set.of(keys));
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return trimToNull(String.valueOf(value));
    }

    private Object findFirstValueByKeys(Object node, Set<String> keys) {
        if (node == null || keys == null || keys.isEmpty()) {
            return null;
        }
        if (node instanceof Map<?, ?> map) {
            for (String key : keys) {
                if (map.containsKey(key)) {
                    Object direct = map.get(key);
                    if (direct != null && !(direct instanceof Map<?, ?>) && !(direct instanceof List<?>)
                            && !String.valueOf(direct).isBlank()) {
                        return direct;
                    }
                }
            }
            for (Object value : map.values()) {
                Object nested = findFirstValueByKeys(value, keys);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                Object nested = findFirstValueByKeys(item, keys);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String normalizeCategoryText(String value) {
        String normalized = trimToNull(decodeHtmlEntities(value));
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace('\u00A0', ' ')
                .replace("&gt;", ">")
                .replace(" > ", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private CategoryInfo extractCategory(Document doc) {
        CategoryInfo info = new CategoryInfo();

        Element categoryLine = null;
        for (Element el : doc.select("div")) {
            if (el.ownText() != null && el.ownText().contains("类目")) {
                categoryLine = el;
                break;
            }
        }
        if (categoryLine != null) {
            Element catSpan = categoryLine.selectFirst("span.goods-operation-items");
            String shortCat = null;
            if (catSpan != null) {
                shortCat = catSpan.ownText();
                if (shortCat != null) shortCat = shortCat.trim();
                if (shortCat != null && shortCat.isBlank()) shortCat = null;
            }
            if (shortCat != null && shortCat.contains("类目")) {
                shortCat = null;
            }
            info.productCategory = shortCat;

            String tooltip = textOrNull(categoryLine.selectFirst(".goods-operation-tooltip"));
            if (tooltip != null) {
                info.originalCategory = normalizeCategoryText(tooltip);
            }
        }

        if (info.originalCategory == null || info.originalCategory.isBlank()) {
            info.originalCategory = info.productCategory;
        }
        if (info.productCategory == null || info.productCategory.isBlank()) {
            info.productCategory = info.originalCategory;
        }
        info.leafCategoryName = info.productCategory;
        return info;
    }

    private String extractPanelMetric(Document doc, String label) {
        for (Element el : doc.select("div")) {
            if (el.ownText() != null && el.ownText().contains(label)) {
                Element span = el.selectFirst("span");
                if (span != null) {
                    String v = span.text();
                    if (v != null && !v.isBlank()) {
                        return v.trim();
                    }
                }
            }
        }
        return null;
    }

    private String extractTradeInfoAnnualSales(Document doc) {
        String s = textOrNull(first(doc, "#productTitle .trade-info"));
        if (s == null) return null;
        Matcher m = Pattern.compile("一年内\\s*([0-9]+\\+?)").matcher(s);
        if (m.find()) return m.group(1);
        return null;
    }

    private BigDecimal extractServiceScore(Document doc) {
        return extractDecimalMetric(doc,
                "店铺服务分",
                Pattern.compile("服务\\s*([0-9]+(?:\\.[0-9]+)?)\\s*分"));
    }

    private BigDecimal extractRatingScore(Document doc) {
        Element score = first(doc, "#productEvaluation .label-desc-rate");
        if (score != null) {
            Element hl = score.parent() == null ? null : score.parent().selectFirst("em.hl");
            if (hl != null) {
                return parseBigDecimal(hl.text());
            }
        }
        String t = textOrNull(first(doc, "#productTitle"));
        if (t != null) {
            Matcher m = Pattern.compile("\\b([0-9]\\.[0-9])\\b").matcher(t);
            if (m.find()) {
                return parseBigDecimal(m.group(1));
            }
        }
        return null;
    }

    private Integer extractReviewCount(Document doc) {
        String t = textOrNull(first(doc, "#productTitle"));
        if (t == null) return null;
        Matcher m = Pattern.compile("(\\d+)条评价").matcher(t);
        if (m.find()) return parseFirstInt(m.group(1));
        return null;
    }

    private Integer extractCollectCount(Document doc) {
        String t = textOrNull(first(doc, "#submitOrder"));
        if (t == null) return null;
        Matcher m = Pattern.compile("收藏\\((\\d+)\\)").matcher(t);
        if (m.find()) return parseFirstInt(m.group(1));
        return null;
    }

    private BigDecimal extractRepeatCustomerRate(Document doc) {
        return extractDecimalMetric(doc,
                "店铺回头率",
                Pattern.compile("回头率\\s*([0-9]+(?:\\.[0-9]+)?)%"));
    }

    private BigDecimal extractOnTimeDeliveryRate(Document doc) {
        return extractDecimalMetric(doc,
                "准时发货率",
                Pattern.compile("准时发货率\\s*([0-9]+(?:\\.[0-9]+)?)%"));
    }

    private BigDecimal extractShopPositiveRate(Document doc) {
        return extractDecimalMetric(doc,
                "店铺好评率",
                Pattern.compile("店铺好评率\\s*([0-9]+(?:\\.[0-9]+)?)%"));
    }

    private Boolean extractPowerSeller(Document doc) {
        if (doc == null) {
            return null;
        }
        if (!doc.select("workbench-i18n[name=power_seller], .sellerCenterTitle workbench-i18n[name=power_seller]").isEmpty()) {
            return Boolean.TRUE;
        }
        String wholeHtml = doc.html();
        if (wholeHtml != null && (wholeHtml.contains("name=\"power_seller\"") || wholeHtml.contains("实力商家"))) {
            return Boolean.TRUE;
        }
        Element shopNavigation = first(doc, "#shopNavigation");
        if (shopNavigation == null) {
            return null;
        }
        if (first(shopNavigation, "workbench-i18n[name=power_seller]") != null) {
            return Boolean.TRUE;
        }
        String html = shopNavigation.html();
        if (html != null && (html.contains("power_seller") || html.contains("实力商家"))) {
            return Boolean.TRUE;
        }
        String text = normalizeShopNavigationText(shopNavigation);
        if (text == null) {
            return null;
        }
        return text.contains("实力商家");
    }

    private String extractSettledYearsText(Document doc) {
        Element explicit = first(doc, "#shopNavigation .shop-tp-year");
        String text = trimToNull(textOrNull(explicit));
        if (text != null) {
            return text;
        }
        String shopText = normalizeShopNavigationText(first(doc, "#shopNavigation"));
        return extractFirstGroup(shopText, Pattern.compile("(入驻\\s*\\d+\\s*年|成立\\s*\\d+\\s*年|新会员入驻)"));
    }

    private String extractMainBusiness(Document doc) {
        String text = trimToNull(textOrNull(first(doc, "#shopNavigation .shop-category-name")));
        if (text != null) {
            return stripMainBusinessPrefix(text);
        }
        String shopText = normalizeShopNavigationText(first(doc, "#shopNavigation"));
        String matched = extractFirstGroup(shopText,
                Pattern.compile("主营[:：]\\s*(.+?)(?:店铺回头率|店铺服务分|准时发货率|店铺好评率|$)"));
        return stripMainBusinessPrefix(matched);
    }

    private BigDecimal extractDecimalMetric(Document doc, String label, Pattern fallbackPattern) {
        String value = extractShopMetricValue(doc, label);
        BigDecimal parsed = extractLeadingDecimal(value);
        if (parsed != null) {
            return parsed;
        }
        String shopText = normalizeShopNavigationText(first(doc, "#shopNavigation"));
        return parseBigDecimal(extractFirstGroup(shopText, fallbackPattern));
    }

    private String extractShopMetricValue(Document doc, String label) {
        if (doc == null || label == null || label.isBlank()) {
            return null;
        }
        for (Element item : doc.select("#shopNavigation .shop-data-item")) {
            String currentLabel = trimToNull(textOrNull(first(item, ".shop-data-item-label")));
            if (!label.equals(currentLabel)) {
                continue;
            }
            String value = trimToNull(textOrNull(first(item, ".shop-data-item-value")));
            if (value != null) {
                return value;
            }
            return trimToNull(item.ownText());
        }
        return null;
    }

    private String normalizeShopNavigationText(Element shopNavigation) {
        String text = textOrNull(shopNavigation);
        if (text == null) {
            return null;
        }
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private BigDecimal extractLeadingDecimal(String value) {
        return parseBigDecimal(extractFirstGroup(value, Pattern.compile("([0-9]+(?:\\.[0-9]+)?)")));
    }

    private String extractFirstGroup(String text, Pattern pattern) {
        if (text == null || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return trimToNull(matcher.group(1));
    }

    private String stripMainBusinessPrefix(String value) {
        if (value == null) {
            return null;
        }
        String normalized = trimToNull(value.replace('\u00A0', ' '));
        if (normalized == null) {
            return null;
        }
        return normalized.replaceFirst("^主营[:：]\\s*", "").trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isBlank()) return null;
        int idx = t.indexOf(" - ");
        if (idx > 0) {
            return t.substring(0, idx).trim();
        }
        return t;
    }

    private boolean isImageUrl(String u) {
        if (u == null) return false;
        String s = u.toLowerCase(Locale.ROOT);
        if (s.startsWith("data:")) return false;
        return s.contains(".jpg") || s.contains(".jpeg") || s.contains(".png") || s.contains(".webp") || s.contains(".gif");
    }

    private String pickImgUrl(Element img) {
        if (img == null) return null;
        String u = firstNonBlank(
                img.attr("src"),
                img.attr("data-src"),
                img.attr("data-lazy-src"),
                img.attr("data-original"),
                img.attr("data-ks-lazyload"),
                img.attr("data-lazyload"),
                img.attr("data-lazyload-src"),
                img.attr("data-srcset"),
                img.attr("srcset")
        );

        if (u == null || u.isBlank()) return null;
        u = u.trim();

        if (u.contains(",") && (u.contains(" ") || u.contains("\t"))) {
            String first = u.split(",")[0].trim();
            int sp = first.indexOf(' ');
            if (sp > 0) first = first.substring(0, sp).trim();
            if (!first.isBlank()) return first;
        }
        return u;
    }

    private String firstNonBlank(String... items) {
        if (items == null) return null;
        for (String it : items) {
            if (it != null && !it.isBlank()) return it;
        }
        return null;
    }

    private String pickVideoUrl(Element video) {
        if (video == null) return null;
        String u = video.attr("src");
        if (u == null || u.isBlank()) {
            u = video.attr("data-src");
        }
        u = (u == null || u.isBlank()) ? null : u.trim();
        return normalizeUrl(u);
    }

    private String normalizeUrl(String u) {
        if (u == null) return null;
        String s = u.trim();
        if (s.isBlank()) return null;
        if (s.startsWith("//")) return "https:" + s;
        return s;
    }

    private String matchFirst(String s, Pattern p) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        if (m.find()) return m.group(1);
        return null;
    }

    private Element first(Document doc, String css) {
        return doc == null ? null : doc.selectFirst(css);
    }

    private Element first(Element root, String css) {
        return root == null ? null : root.selectFirst(css);
    }

    private String textOrNull(Element el) {
        if (el == null) return null;
        String t = el.text();
        return t == null ? null : t.trim();
    }

    private String attrOrNull(Element el, String attr) {
        if (el == null) return null;
        String v = el.attr(attr);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private BigDecimal parseMoney(String text) {
        if (text == null) return null;
        String cleaned = text.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String text) {
        if (text == null) return null;
        String cleaned = text.trim();
        if (cleaned.isBlank()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseFirstInt(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(\\d+)").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJsonOrNull(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static class ParsedProduct {
        private String productName;
        private String productMainImage;
        private String productCategory;
        private String originalCategory;
        private String leafCategoryName;
        private String leafCategoryId;
        private String postCategoryId;
        private String secondCategoryId;
        private String topCategoryId;
        private String carouselImagesJson;
        private String carouselThumbImagesJson;
        private String carouselVideoUrl;
        private String detailImagesJson;
        private BigDecimal netWeight;
        private BigDecimal packagingWeight;
        private BigDecimal packagingLength;
        private BigDecimal packagingWidth;
        private BigDecimal packagingHeight;
        private String packagingDimensions;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Integer moq;
        private String moqText;
        private String priceStepsJson;
        private Boolean hasSevereInventory;
        private String skuDataJson;
        private String skuModelJson;
        private String attributesDataJson;
        private String customMadeSpecsJson;
        private String monthlyConsignment;
        private String monthlySales;
        private BigDecimal serviceScore;
        private String shippingLocation;
        private String companyLocation;
        private String companyName;
        private BigDecimal ratingScore;
        private Integer reviewCount;
        private Integer collectCount;
        private String annualSales;
        private BigDecimal repeatCustomerRate;
        private BigDecimal onTimeDeliveryRate;
        private BigDecimal shopPositiveRate;
        private Boolean powerSeller;
        private String settledYearsText;
        private String mainBusiness;
        private String originalContent;
        private String originalHtml;
        private String productId;
        private String alibabaProductId;
        private String productUrl;

        private String shippingServicesInfo;
        private BigDecimal baseFreight;

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getProductMainImage() {
            return productMainImage;
        }

        public void setProductMainImage(String productMainImage) {
            this.productMainImage = productMainImage;
        }

        public String getProductCategory() {
            return productCategory;
        }

        public void setProductCategory(String productCategory) {
            this.productCategory = productCategory;
        }

        public String getOriginalCategory() {
            return originalCategory;
        }

        public void setOriginalCategory(String originalCategory) {
            this.originalCategory = originalCategory;
        }

        public String getLeafCategoryName() {
            return leafCategoryName;
        }

        public void setLeafCategoryName(String leafCategoryName) {
            this.leafCategoryName = leafCategoryName;
        }

        public String getLeafCategoryId() {
            return leafCategoryId;
        }

        public void setLeafCategoryId(String leafCategoryId) {
            this.leafCategoryId = leafCategoryId;
        }

        public String getPostCategoryId() {
            return postCategoryId;
        }

        public void setPostCategoryId(String postCategoryId) {
            this.postCategoryId = postCategoryId;
        }

        public String getSecondCategoryId() {
            return secondCategoryId;
        }

        public void setSecondCategoryId(String secondCategoryId) {
            this.secondCategoryId = secondCategoryId;
        }

        public String getTopCategoryId() {
            return topCategoryId;
        }

        public void setTopCategoryId(String topCategoryId) {
            this.topCategoryId = topCategoryId;
        }

        public String getCarouselImagesJson() {
            return carouselImagesJson;
        }

        public void setCarouselImagesJson(String carouselImagesJson) {
            this.carouselImagesJson = carouselImagesJson;
        }

        public String getCarouselThumbImagesJson() {
            return carouselThumbImagesJson;
        }

        public void setCarouselThumbImagesJson(String carouselThumbImagesJson) {
            this.carouselThumbImagesJson = carouselThumbImagesJson;
        }

        public String getCarouselVideoUrl() {
            return carouselVideoUrl;
        }

        public void setCarouselVideoUrl(String carouselVideoUrl) {
            this.carouselVideoUrl = carouselVideoUrl;
        }

        public String getDetailImagesJson() {
            return detailImagesJson;
        }

        public void setDetailImagesJson(String detailImagesJson) {
            this.detailImagesJson = detailImagesJson;
        }

        public BigDecimal getNetWeight() {
            return netWeight;
        }

        public void setNetWeight(BigDecimal netWeight) {
            this.netWeight = netWeight;
        }

        public BigDecimal getPackagingWeight() {
            return packagingWeight;
        }

        public void setPackagingWeight(BigDecimal packagingWeight) {
            this.packagingWeight = packagingWeight;
        }

        public BigDecimal getPackagingLength() {
            return packagingLength;
        }

        public void setPackagingLength(BigDecimal packagingLength) {
            this.packagingLength = packagingLength;
        }

        public BigDecimal getPackagingWidth() {
            return packagingWidth;
        }

        public void setPackagingWidth(BigDecimal packagingWidth) {
            this.packagingWidth = packagingWidth;
        }

        public BigDecimal getPackagingHeight() {
            return packagingHeight;
        }

        public void setPackagingHeight(BigDecimal packagingHeight) {
            this.packagingHeight = packagingHeight;
        }

        public String getPackagingDimensions() {
            return packagingDimensions;
        }

        public void setPackagingDimensions(String packagingDimensions) {
            this.packagingDimensions = packagingDimensions;
        }

        public BigDecimal getMinPrice() {
            return minPrice;
        }

        public void setMinPrice(BigDecimal minPrice) {
            this.minPrice = minPrice;
        }

        public BigDecimal getMaxPrice() {
            return maxPrice;
        }

        public void setMaxPrice(BigDecimal maxPrice) {
            this.maxPrice = maxPrice;
        }

        public Integer getMoq() {
            return moq;
        }

        public void setMoq(Integer moq) {
            this.moq = moq;
        }

        public String getMoqText() {
            return moqText;
        }

        public void setMoqText(String moqText) {
            this.moqText = moqText;
        }

        public String getPriceStepsJson() {
            return priceStepsJson;
        }

        public void setPriceStepsJson(String priceStepsJson) {
            this.priceStepsJson = priceStepsJson;
        }

        public Boolean getHasSevereInventory() {
            return hasSevereInventory;
        }

        public void setHasSevereInventory(Boolean hasSevereInventory) {
            this.hasSevereInventory = hasSevereInventory;
        }

        public String getSkuDataJson() {
            return skuDataJson;
        }

        public void setSkuDataJson(String skuDataJson) {
            this.skuDataJson = skuDataJson;
        }

        public String getSkuModelJson() {
            return skuModelJson;
        }

        public void setSkuModelJson(String skuModelJson) {
            this.skuModelJson = skuModelJson;
        }

        public String getAttributesDataJson() {
            return attributesDataJson;
        }

        public void setAttributesDataJson(String attributesDataJson) {
            this.attributesDataJson = attributesDataJson;
        }

        public String getCustomMadeSpecsJson() {
            return customMadeSpecsJson;
        }

        public void setCustomMadeSpecsJson(String customMadeSpecsJson) {
            this.customMadeSpecsJson = customMadeSpecsJson;
        }

        public String getMonthlyConsignment() {
            return monthlyConsignment;
        }

        public void setMonthlyConsignment(String monthlyConsignment) {
            this.monthlyConsignment = monthlyConsignment;
        }

        public String getMonthlySales() {
            return monthlySales;
        }

        public void setMonthlySales(String monthlySales) {
            this.monthlySales = monthlySales;
        }

        public BigDecimal getServiceScore() {
            return serviceScore;
        }

        public void setServiceScore(BigDecimal serviceScore) {
            this.serviceScore = serviceScore;
        }

        public String getShippingLocation() {
            return shippingLocation;
        }

        public void setShippingLocation(String shippingLocation) {
            this.shippingLocation = shippingLocation;
        }

        public String getCompanyLocation() {
            return companyLocation;
        }

        public void setCompanyLocation(String companyLocation) {
            this.companyLocation = companyLocation;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public BigDecimal getRatingScore() {
            return ratingScore;
        }

        public void setRatingScore(BigDecimal ratingScore) {
            this.ratingScore = ratingScore;
        }

        public Integer getReviewCount() {
            return reviewCount;
        }

        public void setReviewCount(Integer reviewCount) {
            this.reviewCount = reviewCount;
        }

        public Integer getCollectCount() {
            return collectCount;
        }

        public void setCollectCount(Integer collectCount) {
            this.collectCount = collectCount;
        }

        public String getAnnualSales() {
            return annualSales;
        }

        public void setAnnualSales(String annualSales) {
            this.annualSales = annualSales;
        }

        public BigDecimal getRepeatCustomerRate() {
            return repeatCustomerRate;
        }

        public void setRepeatCustomerRate(BigDecimal repeatCustomerRate) {
            this.repeatCustomerRate = repeatCustomerRate;
        }

        public BigDecimal getOnTimeDeliveryRate() {
            return onTimeDeliveryRate;
        }

        public void setOnTimeDeliveryRate(BigDecimal onTimeDeliveryRate) {
            this.onTimeDeliveryRate = onTimeDeliveryRate;
        }

        public BigDecimal getShopPositiveRate() {
            return shopPositiveRate;
        }

        public void setShopPositiveRate(BigDecimal shopPositiveRate) {
            this.shopPositiveRate = shopPositiveRate;
        }

        public Boolean getPowerSeller() {
            return powerSeller;
        }

        public void setPowerSeller(Boolean powerSeller) {
            this.powerSeller = powerSeller;
        }

        public String getSettledYearsText() {
            return settledYearsText;
        }

        public void setSettledYearsText(String settledYearsText) {
            this.settledYearsText = settledYearsText;
        }

        public String getMainBusiness() {
            return mainBusiness;
        }

        public void setMainBusiness(String mainBusiness) {
            this.mainBusiness = mainBusiness;
        }

        public String getOriginalContent() {
            return originalContent;
        }

        public void setOriginalContent(String originalContent) {
            this.originalContent = originalContent;
        }

        public String getOriginalHtml() {
            return originalHtml;
        }

        public void setOriginalHtml(String originalHtml) {
            this.originalHtml = originalHtml;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getAlibabaProductId() {
            return alibabaProductId;
        }

        public void setAlibabaProductId(String alibabaProductId) {
            this.alibabaProductId = alibabaProductId;
        }

        public String getProductUrl() {
            return productUrl;
        }

        public void setProductUrl(String productUrl) {
            this.productUrl = productUrl;
        }

        public String getShippingServicesInfo() {
            return shippingServicesInfo;
        }

        public void setShippingServicesInfo(String shippingServicesInfo) {
            this.shippingServicesInfo = shippingServicesInfo;
        }

        public BigDecimal getBaseFreight() {
            return baseFreight;
        }

        public void setBaseFreight(BigDecimal baseFreight) {
            this.baseFreight = baseFreight;
        }
    }

    private static class ShippingServicesInfo {
        String moduleText;
        BigDecimal baseFreight;
    }

    private static class CarouselMedia {
        List<String> bigImages = Collections.emptyList();
        List<String> thumbImages = Collections.emptyList();
        String videoUrl;
    }

    private static class CategoryInfo {
        String productCategory;
        String originalCategory;
        String leafCategoryName;
        String leafCategoryId;
        String postCategoryId;
        String secondCategoryId;
        String topCategoryId;
    }

    private static class PackagingInfo {
        BigDecimal length;
        BigDecimal width;
        BigDecimal height;
        BigDecimal weight;
        String dimensions;

        Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (length != null) m.put("lengthCm", length);
            if (width != null) m.put("widthCm", width);
            if (height != null) m.put("heightCm", height);
            if (weight != null) m.put("weightG", weight);
            if (dimensions != null) m.put("dimensions", dimensions);
            return m;
        }
    }

    private static class PriceInfo {
        BigDecimal minPrice;
        BigDecimal maxPrice;
        Integer moq;
        String moqText;
        String priceStepsJson;
    }

    private static class SkuInfo {
        String skuDataJson;
        Integer totalStock;
        List<Map<String, Object>> skus = Collections.emptyList();
    }

    private static class SkuModel {
        String skuModelJson;
        Map<String, Map<String, Object>> skuMap;
    }
}
