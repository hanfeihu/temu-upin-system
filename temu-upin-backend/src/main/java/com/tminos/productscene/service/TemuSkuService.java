package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuSkuDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionSku;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.repository.ProductCollectionSkuRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuSkuService {
    private static final BigDecimal SUPPLY_PRICE_FIXED_ADD = new BigDecimal("6");
    private static final BigDecimal SUPPLY_PRICE_WEIGHT_RATE_PER_G = new BigDecimal("0.069");
    private static final BigDecimal SUPPLY_PRICE_MULTIPLIER = new BigDecimal("6");

    private final ProductCollectionService productCollectionService;
    private final ProductCollectionSkuRepository originSkuRepo;
    private final ProductCollectionTemuSkuRepository temuSkuRepo;
    private final TemuSkuSpecTranslateService skuSpecTranslateService;

    public TemuSkuService(ProductCollectionService productCollectionService,
                          ProductCollectionSkuRepository originSkuRepo,
                          ProductCollectionTemuSkuRepository temuSkuRepo,
                          TemuSkuSpecTranslateService skuSpecTranslateService) {
        this.productCollectionService = productCollectionService;
        this.originSkuRepo = originSkuRepo;
        this.temuSkuRepo = temuSkuRepo;
        this.skuSpecTranslateService = skuSpecTranslateService;
    }

    @Transactional(readOnly = true)
    public List<TemuSkuDTO.TemuSkuRow> list(Long spuId) {
        if (spuId == null) return Collections.emptyList();
        return temuSkuRepo.findBySpuIdOrderByIdAsc(spuId)
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public List<TemuSkuDTO.TemuSkuRow> initFromOrigin(Long spuId, boolean force) {
        if (spuId == null) return Collections.emptyList();

        List<ProductCollectionTemuSku> existing = temuSkuRepo.findBySpuIdOrderByIdAsc(spuId);
        if (!force && existing != null && !existing.isEmpty()) {
            return existing.stream().map(this::toRow).toList();
        }

        if (force) {
            temuSkuRepo.deleteBySpuId(spuId);
        }

        ProductCollection pc = productCollectionService.get(spuId);
        BigDecimal baseFreight = pc.getBaseFreight();
        if (baseFreight == null) baseFreight = BigDecimal.ZERO;

        Integer defaultWeightG = computeDefaultWeightG(pc);
        Dim dim = computeDefaultDim(pc);

        List<ProductCollectionSku> originSkus = originSkuRepo.findBySpuId(spuId);
        List<ProductCollectionTemuSku> out = new ArrayList<>();
        int seq = 0;
        for (ProductCollectionSku o : originSkus) {
            if (o == null) continue;
            seq++;
            BigDecimal originPrice = o.getPrice();
            BigDecimal supply = computeSupplyPrice(originPrice, baseFreight, defaultWeightG);

            ProductCollectionTemuSku t = new ProductCollectionTemuSku();
            t.setSpuId(spuId);
            t.setTemuSkuId(o.getSkuId());
            t.setOriginSkuId(o.getSkuId());
            String fallbackSpec = StringUtils.hasText(o.getSkuId()) ? ("SKU-" + o.getSkuId().trim()) : ("Option-" + seq);
            t.setSpecKey(normalizeSpecKeyForTemuDisplay(o.getSpecKey(), fallbackSpec));
            t.setSpecJson(translateSpecJsonValues(o.getSpecJson()));
            t.setImage(o.getImage());
            t.setOriginPrice(originPrice);
            t.setSupplyPrice(supply);
            t.setWeightG(defaultWeightG);
            t.setLengthCm(enforceMinSideCm(dim.lengthCm));
            t.setWidthCm(enforceMinSideCm(dim.widthCm));
            t.setHeightCm(enforceMinSideCm(dim.heightCm));
            out.add(t);
        }

        temuSkuRepo.saveAll(out);
        return out.stream().map(this::toRow).toList();
    }

    @Transactional
    public List<TemuSkuDTO.TemuSkuRow> saveAll(Long spuId, List<TemuSkuDTO.TemuSkuRow> rows) {
        if (spuId == null) return Collections.emptyList();
        productCollectionService.get(spuId);

        if (rows == null) rows = Collections.emptyList();
        List<ProductCollectionTemuSku> toSave = new ArrayList<>();
        int seq = 0;
        for (TemuSkuDTO.TemuSkuRow r : rows) {
            if (r == null) continue;
            seq++;
            // allow empty row only if it has at least some meaningful content
            if (!StringUtils.hasText(r.getSpecKey())
                    && !StringUtils.hasText(r.getSpecJson())
                    && !StringUtils.hasText(r.getTemuSkuId())
                    && !StringUtils.hasText(r.getOriginSkuId())) {
                continue;
            }

            ProductCollectionTemuSku e = new ProductCollectionTemuSku();
            e.setId(r.getId());
            e.setSpuId(spuId);
            e.setTemuSkuId(trimOrNull(r.getTemuSkuId()));
            e.setOriginSkuId(trimOrNull(r.getOriginSkuId()));
            String fallbackSpec = StringUtils.hasText(r.getOriginSkuId()) ? ("SKU-" + r.getOriginSkuId().trim()) : ("Option-" + seq);
            e.setSpecKey(normalizeSpecKeyForTemuDisplay(r.getSpecKey(), fallbackSpec));
            e.setSpecJson(translateSpecJsonValues(r.getSpecJson()));
            e.setImage(trimOrNull(r.getImage()));
            e.setOriginPrice(r.getOriginPrice());
            e.setSupplyPrice(r.getSupplyPrice());
            Integer wg = r.getWeightG();
            if (wg != null && wg > 0 && wg < 30) wg = 30;
            e.setWeightG(wg);
            e.setLengthCm(enforceMinSideCm(r.getLengthCm()));
            e.setWidthCm(enforceMinSideCm(r.getWidthCm()));
            e.setHeightCm(enforceMinSideCm(r.getHeightCm()));
            toSave.add(e);
        }

        // replace mode: keep it simple and deterministic
        temuSkuRepo.deleteBySpuId(spuId);
        List<ProductCollectionTemuSku> saved = temuSkuRepo.saveAll(toSave);
        return saved.stream().map(this::toRow).toList();
    }

    @Transactional
    public TemuSkuDTO.TemuSkuRow updateImage(Long spuId, Long skuId, String imageUrl) {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        if (skuId == null) throw new IllegalArgumentException("skuId is required");
        productCollectionService.get(spuId);
        ProductCollectionTemuSku row = temuSkuRepo.findById(skuId)
                .orElseThrow(() -> new IllegalArgumentException("TEMU SKU 不存在"));
        if (row.getSpuId() == null || !row.getSpuId().equals(spuId)) {
            throw new IllegalArgumentException("TEMU SKU 不属于当前商品");
        }
        row.setImage(trimOrNull(imageUrl));
        return toRow(temuSkuRepo.save(row));
    }

    public BigDecimal computeSupplyPrice(BigDecimal originSkuPrice, BigDecimal baseFreight, Integer weightG) {
        BigDecimal p = originSkuPrice == null ? BigDecimal.ZERO : originSkuPrice;
        BigDecimal freight = baseFreight == null ? BigDecimal.ZERO : baseFreight;
        int g = weightG == null ? 150 : weightG;

        BigDecimal goodsValue = p.add(freight);
        return calculateSupplyPrice(goodsValue, g);
    }

    private BigDecimal calculateSupplyPrice(BigDecimal goodsValue, int weightG) {
        BigDecimal safeGoodsValue = goodsValue == null ? BigDecimal.ZERO : goodsValue;
        BigDecimal weightCost = SUPPLY_PRICE_WEIGHT_RATE_PER_G.multiply(new BigDecimal(String.valueOf(weightG)));
        return safeGoodsValue
                .add(SUPPLY_PRICE_FIXED_ADD)
                .add(weightCost)
                .multiply(SUPPLY_PRICE_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Integer computeDefaultWeightG(ProductCollection pc) {
        BigDecimal w = pc == null ? null : pc.getPackagingWeight();
        if (w == null) {
            w = pc == null ? null : pc.getNetWeight();
        }
        if (w == null) return 150;

        int g;
        try {
            g = w.setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (Exception ignored) {
            g = 150;
        }
        if (g < 30) return 30;
        return g;
    }

    private Dim computeDefaultDim(ProductCollection pc) {
        BigDecimal l = pc == null ? null : pc.getPackagingLength();
        BigDecimal w = pc == null ? null : pc.getPackagingWidth();
        BigDecimal h = pc == null ? null : pc.getPackagingHeight();
        if (l == null || w == null || h == null) {
            return new Dim(new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("5"));
        }
        return new Dim(l, w, h);
    }

    private TemuSkuDTO.TemuSkuRow toRow(ProductCollectionTemuSku e) {
        TemuSkuDTO.TemuSkuRow r = new TemuSkuDTO.TemuSkuRow();
        r.setId(e.getId());
        r.setTemuSkuId(e.getTemuSkuId());
        r.setOriginSkuId(e.getOriginSkuId());
        r.setSpecKey(e.getSpecKey());
        r.setSpecJson(e.getSpecJson());
        r.setImage(e.getImage());
        r.setOriginPrice(e.getOriginPrice());
        r.setSupplyPrice(e.getSupplyPrice());
        r.setWeightG(e.getWeightG());
        r.setLengthCm(e.getLengthCm());
        r.setWidthCm(e.getWidthCm());
        r.setHeightCm(e.getHeightCm());
        return r;
    }

    private String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.trim();
    }

    /**
     * TEMU category constraint example: Shortest Side must be >= 0.2cm.
     * We enforce a conservative per-side minimum here so that user inputs like 0.1 won't fail publish.
     */
    private BigDecimal enforceMinSideCm(BigDecimal v) {
        if (v == null) return null;
        try {
            // only adjust when it's positive but too small
            if (v.compareTo(BigDecimal.ZERO) > 0 && v.compareTo(new BigDecimal("0.2")) < 0) {
                return new BigDecimal("0.2");
            }
            return v;
        } catch (Exception ignored) {
            return v;
        }
    }

    /**
     * Normalize TEMU "specKey" for display and later publish:
     * - If contains CJK, translate to English (best-effort), then use a local
     *   fallback dictionary. If both miss, keep the original CJK text instead
     *   of silently dropping important SKU information.
     * - Remove forbidden characters like '.' and brackets
     * - Do not keep '>' separator; use spaces for display
     */
    private String normalizeSpecKeyForTemuDisplay(String specKey, String fallback) {
        if (!StringUtils.hasText(specKey)) {
            return StringUtils.hasText(fallback) ? fallback.trim() : null;
        }

        String t = specKey.trim();
        if (containsCjk(t)) {
            String tr = skuSpecTranslateService.translateSpecKey(t);
            if (!StringUtils.hasText(tr)) {
                tr = translateSkuTextFallback(t);
            }
            if (StringUtils.hasText(tr)) {
                t = tr.trim();
            }
        }

        // Remove chars that break TEMU display
        t = t
                .replace('【', ' ')
                .replace('】', ' ')
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('.', ' ')
                .replace('．', ' ')
                .replace('。', ' ');

        // Use spaces instead of hierarchical delimiter
        t = t.replace('>', ' ');

        // Keep common safe characters, but do not drop CJK when translation is unavailable.
        t = t
                .replaceAll("[^A-Za-z0-9\\u4e00-\\u9fff\\-\\_\\,\\/\\+\\&\\%\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!StringUtils.hasText(t)) {
            return StringUtils.hasText(fallback) ? fallback.trim() : null;
        }
        if (t.length() > 120) {
            t = t.substring(0, 120).trim();
        }
        return t;
    }

    /**
     * Translate values in specJson to English, keeping the original Chinese keys.
     * e.g. {"尺寸":"木叶+双格包"} -> {"尺寸":"Konoha Double-Compartment Bag"}
     */
    private String translateSpecJsonValues(String specJson) {
        if (!StringUtils.hasText(specJson)) return specJson;
        try {
            ObjectMapper om = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = om.readValue(specJson, Map.class);
            Map<String, Object> translated = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String sv && StringUtils.hasText(sv)
                        && !"*".equals(sv.trim()) && containsCjk(sv)) {
                    String tr = skuSpecTranslateService.translatePlainText(sv);
                    translated.put(entry.getKey(), sanitizeSpecValue(StringUtils.hasText(tr) ? tr.trim() : sv));
                } else if (val instanceof String sv) {
                    translated.put(entry.getKey(), sanitizeSpecValue(sv));
                } else {
                    translated.put(entry.getKey(), val);
                }
            }
            return om.writeValueAsString(translated);
        } catch (Exception e) {
            return specJson;
        }
    }

    /**
     * Remove brackets and other special characters that TEMU rejects from spec values.
     */
    private String sanitizeSpecValue(String value) {
        if (!StringUtils.hasText(value)) return value;
        String s = value
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('【', ' ')
                .replace('】', ' ')
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('「', ' ')
                .replace('」', ' ')
                .replace('『', ' ')
                .replace('』', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(s) ? s : value.trim();
    }

    private String translateSkuTextFallback(String text) {
        if (!StringUtils.hasText(text)) return null;
        String s = text.trim();
        Map<String, String> dict = new LinkedHashMap<>();
        dict.put("玫瑰金", "Rose Gold");
        dict.put("香槟金", "Champagne Gold");
        dict.put("浅粉色", "Light Pink");
        dict.put("深粉色", "Dark Pink");
        dict.put("浅蓝色", "Light Blue");
        dict.put("深蓝色", "Dark Blue");
        dict.put("粉红色", "Pink");
        dict.put("粉色", "Pink");
        dict.put("蓝色", "Blue");
        dict.put("红色", "Red");
        dict.put("绿色", "Green");
        dict.put("黄色", "Yellow");
        dict.put("黑色", "Black");
        dict.put("白色", "White");
        dict.put("灰色", "Gray");
        dict.put("紫色", "Purple");
        dict.put("橙色", "Orange");
        dict.put("棕色", "Brown");
        dict.put("金色", "Gold");
        dict.put("银色", "Silver");
        dict.put("透明", "Transparent");
        dict.put("均码", "One Size");
        dict.put("特大号", "XL");
        dict.put("大号", "Large");
        dict.put("中号", "Medium");
        dict.put("小号", "Small");
        dict.put("款", "Style");
        dict.put("色", "Color");
        for (Map.Entry<String, String> e : dict.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return containsCjk(s) ? null : s;
    }

    private boolean containsCjk(String s) {
        if (!StringUtils.hasText(s)) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }

    private record Dim(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
}
