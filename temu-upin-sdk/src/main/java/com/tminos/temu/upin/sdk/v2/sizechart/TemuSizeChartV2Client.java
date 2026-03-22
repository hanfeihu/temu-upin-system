package com.tminos.temu.upin.sdk.v2.sizechart;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.util.HttpClient;
import com.tminos.temu.upin.sdk.v2.util.JsonUtil;
import com.tminos.temu.upin.sdk.v2.util.SignatureUtil;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TEMU size-chart related OpenAPI wrapper.
 *
 * <p>Covered APIs:</p>
 * <ul>
 *   <li>{@code bg.goods.sizecharts.class.get} - query size chart classification for a category</li>
 *   <li>{@code bg.goods.sizecharts.meta.get} - query size chart meta definition</li>
 *   <li>{@code bg.goods.sizecharts.settings.get} - query size chart rule / mapping template</li>
 *   <li>{@code bg.goods.sizecharts.get} - query existing size chart templates</li>
 *   <li>{@code bg.goods.sizecharts.create} - create a base size chart template</li>
 *   <li>{@code bg.goods.sizecharts.template.create} - create a publish-time temporary template from a base template</li>
 * </ul>
 *
 * <p>Implementation notes:</p>
 * <ul>
 *   <li>Preferred endpoint is partner API; fallback endpoint is the PA router when needed.</li>
 *   <li>TEMU sometimes returns {@code errorCode=1000000} while the business result is successful, so this wrapper treats it as success.</li>
 *   <li>Some APIs return {@code result} as a nested JSON string; this wrapper transparently parses that case.</li>
 * </ul>
 */
public class TemuSizeChartV2Client {

    public static final String DATA_TYPE = "JSON";
    public static final String VERSION = "V1";

    public static final String API_CLASS_GET = "bg.goods.sizecharts.class.get";
    public static final String API_META_GET = "bg.goods.sizecharts.meta.get";
    public static final String API_LIST = "bg.goods.sizecharts.get";
    public static final String API_TEMPLATE_CREATE = "bg.goods.sizecharts.template.create";
    public static final String API_SETTINGS_GET = "bg.goods.sizecharts.settings.get";

    private final TemuOpenApiCredentials creds;

    public TemuSizeChartV2Client(TemuOpenApiCredentials creds) {
        if (creds == null) throw new IllegalArgumentException("creds is required");
        if (isBlank(creds.getAccessToken())) throw new IllegalArgumentException("accessToken is required");
        if (isBlank(creds.getAppKey())) throw new IllegalArgumentException("appKey is required");
        if (isBlank(creds.getAppSecret())) throw new IllegalArgumentException("appSecret is required");
        this.creds = creds;
    }

    /**
     * Query size chart class binding.
     *
     * @param catId leaf category id, optional
     * @param classId explicit size class id, optional
     * @return wrapper whose {@code result.sizeSpecClassCat} contains classId / parentClassId / classType
     */
    public TemuApiResponse<SizeChartClassGetResult> getClassInfo(Integer catId, Integer classId) throws Exception {
        Map<String, Object> params = baseParams(API_CLASS_GET);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        return post(params, SizeChartClassGetResult.class);
    }

    /**
     * Query size chart meta definition.
     *
     * <p>The returned meta defines which {@code groupList} and {@code elementList} ids can appear in
     * {@code bg.goods.sizecharts.create -> content.meta} and in each record's {@code values} map.</p>
     */
    public TemuApiResponse<SizeChartMetaGetResult> getMeta(Integer catId, Integer classId) throws Exception {
        Map<String, Object> params = baseParams(API_META_GET);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        return post(params, SizeChartMetaGetResult.class);
    }

    /**
     * Query size chart settings / mapping rules.
     *
     * <p>The returned {@code mappingContent} is useful as a canonical sample for how TEMU expects
     * {@code content.records} and {@code content.meta} to be shaped when calling {@code sizecharts.create}.</p>
     */
    public TemuApiResponse<SizeChartSettingsResult> getSettings(Integer catId, Integer classId) throws Exception {
        Map<String, Object> params = baseParams(API_SETTINGS_GET);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        return post(params, SizeChartSettingsResult.class);
    }

    /** Returns raw JSON for troubleshooting undocumented response shapes. */
    public String getSettingsRaw(Integer catId, Integer classId) throws Exception {
        Map<String, Object> params = baseParams(API_SETTINGS_GET);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        return postRaw(params);
    }

    /**
     * Query already created base size chart templates.
     *
     * @param catId category id, optional
     * @param offset pagination offset, first page is 0
     * @param pageSize page size, optional
     */
    public TemuApiResponse<SizeChartListResult> getTemplates(Integer catId, int offset, Integer pageSize) throws Exception {
        Map<String, Object> params = baseParams(API_LIST);
        if (catId != null) params.put("catId", catId);
        params.put("offset", offset);
        if (pageSize != null) params.put("pageSize", pageSize);
        return post(params, SizeChartListResult.class);
    }

    /** Returns raw JSON template list response for debugging. */
    public String getTemplatesRaw(Integer catId, int offset, Integer pageSize) throws Exception {
        Map<String, Object> params = baseParams(API_LIST);
        if (catId != null) params.put("catId", catId);
        params.put("offset", offset);
        if (pageSize != null) params.put("pageSize", pageSize);
        return postRaw(params);
    }

    /** Returns raw JSON class binding response for debugging. */
    public String getClassInfoRaw(Integer catId, Integer classId) throws Exception {
        Map<String, Object> params = baseParams(API_CLASS_GET);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        return postRaw(params);
    }

    /** Returns raw JSON meta response for debugging. */
    public String getMetaRaw(Integer catId, Integer classId) throws Exception {
        Map<String, Object> params = baseParams(API_META_GET);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        return postRaw(params);
    }

    /**
     * Create a temporary publish-time template from an existing base template businessId.
     *
     * @param businessId base template id returned by {@code bg.goods.sizecharts.get} or {@code bg.goods.sizecharts.create}
     */
    public TemuApiResponse<SizeChartTemplateCreateResult> createTemplate(Long businessId) throws Exception {
        Map<String, Object> params = baseParams(API_TEMPLATE_CREATE);
        params.put("businessId", businessId);
        return post(params, SizeChartTemplateCreateResult.class);
    }

    /**
     * Create a base size chart template.
     *
     * <p>Documented request shape:</p>
     * <ul>
     *   <li>top level: {@code ext}, {@code catId}, {@code classId}, {@code name}, {@code content}, {@code reusable}</li>
     *   <li>{@code content}: {@code records}, {@code meta}, optional {@code generalSizeType}, optional {@code localSizeSource}, optional {@code bodyRecords}, optional {@code bodyMeta}</li>
     *   <li>{@code content.records[*].values}: map of meta-id -> value</li>
     *   <li>{@code content.meta.groupList[*]}: required size group definitions</li>
     *   <li>{@code content.meta.elementList[*]}: required measurement element definitions</li>
     * </ul>
     */
    public TemuApiResponse<SizeChartCreateResult> createSizeChart(Map<String, Object> content,
                                                                  Integer catId,
                                                                  Integer classId,
                                                                  String name,
                                                                  Boolean reusable,
                                                                  Map<String, Object> ext) throws Exception {
        Map<String, Object> params = baseParams("bg.goods.sizecharts.create");
        params.put("content", content);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        if (!isBlank(name)) params.put("name", name);
        if (reusable != null) params.put("reusable", reusable);
        if (ext != null && !ext.isEmpty()) params.put("ext", ext);
        return post(params, SizeChartCreateResult.class);
    }

    /** Returns raw JSON response for size chart create troubleshooting. */
    public String createSizeChartRaw(Map<String, Object> content,
                                     Integer catId,
                                     Integer classId,
                                     String name,
                                     Boolean reusable,
                                     Map<String, Object> ext) throws Exception {
        Map<String, Object> params = baseParams("bg.goods.sizecharts.create");
        params.put("content", content);
        if (catId != null) params.put("catId", catId);
        if (classId != null) params.put("classId", classId);
        if (!isBlank(name)) params.put("name", name);
        if (reusable != null) params.put("reusable", reusable);
        if (ext != null && !ext.isEmpty()) params.put("ext", ext);
        return postRaw(params);
    }

    private <T> TemuApiResponse<T> post(Map<String, Object> params, Class<T> resultType) throws Exception {
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);
        TemuApiResponse<T> first = parseResponse(HttpClient.sendPostRequest(TemuOpenApiEndpoints.API_BASE_URL_PA, json), resultType);
        if (first != null && (first.isSuccess() || !isBlank(first.getErrorMsg()))) {
            return first;
        }
        TemuApiResponse<T> second = parseResponse(HttpClient.sendPostRequest(TemuOpenApiEndpoints.API_BASE_URL, json), resultType);
        return second == null ? first : second;
    }

    private String postRaw(Map<String, Object> params) throws Exception {
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);
        String first = HttpClient.sendPostRequest(TemuOpenApiEndpoints.API_BASE_URL_PA, json);
        if (first != null && !first.isBlank()) return first;
        return HttpClient.sendPostRequest(TemuOpenApiEndpoints.API_BASE_URL, json);
    }

    private Map<String, Object> baseParams(String api) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", api);
        m.put("app_key", creds.getAppKey());
        m.put("access_token", creds.getAccessToken());
        if (!isBlank(creds.getShopId())) {
            m.put("mall_id", creds.getShopId());
        }
        m.put("data_type", DATA_TYPE);
        m.put("version", VERSION);
        m.put("timestamp", System.currentTimeMillis() / 1000);
        return m;
    }

    private static <T> TemuApiResponse<T> parseResponse(String resp, Class<T> resultType) {
        TemuApiResponse<T> wrapper = new TemuApiResponse<>();
        try {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> probe = gson.fromJson(resp, mapType);
            if (probe == null) {
                wrapper.setSuccess(false);
                wrapper.setErrorMsg("Empty response");
                return wrapper;
            }
            Object success = probe.get("success");
            wrapper.setSuccess(Boolean.TRUE.equals(success));
            Object errorCode = probe.get("errorCode");
            if (errorCode instanceof Number n) {
                wrapper.setErrorCode(n.intValue());
                if (n.intValue() == 1000000) {
                    wrapper.setSuccess(true);
                }
            }
            Object errorMsg = probe.get("errorMsg");
            if (errorMsg instanceof String s) wrapper.setErrorMsg(s);
            Object requestId = probe.get("requestId");
            if (requestId instanceof String s) wrapper.setRequestId(s);
            Object result = probe.get("result");
            if (result != null && resultType != null) {
                if (result instanceof String s && !isBlank(s) && (s.trim().startsWith("{") || s.trim().startsWith("["))) {
                    wrapper.setResult(gson.fromJson(s, resultType));
                } else {
                    wrapper.setResult(gson.fromJson(gson.toJson(result), resultType));
                }
            }
            return wrapper;
        } catch (Exception e) {
            wrapper.setSuccess(false);
            wrapper.setErrorMsg("Failed to parse response: " + e.getMessage() + ", raw=" + resp);
            return wrapper;
        }
    }

    @SuppressWarnings("unchecked")
    private static void sanitizeParams(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Double d) {
                if (d % 1 == 0) entry.setValue(d.longValue());
            } else if (value instanceof Map<?, ?> map) {
                sanitizeParams((Map<String, Object>) map);
            } else if (value instanceof List<?> list) {
                sanitizeList((List<Object>) list);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void sanitizeList(List<Object> list) {
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (value instanceof Double d) {
                if (d % 1 == 0) list.set(i, d.longValue());
            } else if (value instanceof Map<?, ?> map) {
                sanitizeParams((Map<String, Object>) map);
            } else if (value instanceof List<?> child) {
                sanitizeList((List<Object>) child);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Wrapper for {@code bg.goods.sizecharts.class.get}. */
    public static class SizeChartClassGetResult {
        /** Category-class binding result. */
        private SizeSpecClassCat sizeSpecClassCat;
        public SizeSpecClassCat getSizeSpecClassCat() { return sizeSpecClassCat; }
        public void setSizeSpecClassCat(SizeSpecClassCat sizeSpecClassCat) { this.sizeSpecClassCat = sizeSpecClassCat; }
    }

    /**
     * Category to size-class relation.
     *
     * <ul>
     *   <li>{@code catId}: leaf category id</li>
     *   <li>{@code classId}: child size class id</li>
     *   <li>{@code parentClassId}: parent size class id</li>
     *   <li>{@code relatedClassIds}: related class ids, only meaningful for set/bundle class types</li>
     *   <li>{@code classType}: 0 normal, 1 set/bundle</li>
     * </ul>
     */
    public static class SizeSpecClassCat {
        private Integer catId;
        private Integer classId;
        private Integer parentClassId;
        private List<Integer> relatedClassIds;
        private Integer classType;
        public Integer getCatId() { return catId; }
        public Integer getClassId() { return classId; }
        public Integer getParentClassId() { return parentClassId; }
        public List<Integer> getRelatedClassIds() { return relatedClassIds; }
        public Integer getClassType() { return classType; }
    }

    public static class SizeChartMetaGetResult {
        /** Whether the size chart supports flat vs stretched measurement ranges. */
        private Boolean allowRange;
        /** Size chart meta definition used by create/settings APIs. */
        private SizeSpecMeta sizeSpecMeta;
        public Boolean getAllowRange() { return allowRange; }
        public SizeSpecMeta getSizeSpecMeta() { return sizeSpecMeta; }
    }

    /** Wrapper for {@code bg.goods.sizecharts.settings.get}. */
    public static class SizeChartSettingsResult {
        /** Canonical mapping template content, structurally similar to {@code sizecharts.create -> content}. */
        private MappingContent mappingContent;
        /** Rule code returned by TEMU. */
        private Integer code;
        /** Chinese symbolic name of the primary group. */
        private String groupChName;
        /** English symbolic name of the primary group. */
        private String groupEnName;
        /** Allowed size values suggested by TEMU for this rule. */
        private List<String> sizeList;
        public MappingContent getMappingContent() { return mappingContent; }
        public Integer getCode() { return code; }
        public String getGroupChName() { return groupChName; }
        public String getGroupEnName() { return groupEnName; }
        public List<String> getSizeList() { return sizeList; }
    }

    public static class MappingContent {
        /** Canonical sample rows. Each row contains a {@code values} map of meta-id -> value. */
        private List<Map<String, Object>> records;
        /** Canonical sample meta. Usually contains groupList / elementList, sometimes legacy groups / elements too. */
        private Map<String, Object> meta;
        public List<Map<String, Object>> getRecords() { return records; }
        public Map<String, Object> getMeta() { return meta; }
        @Override public String toString() { return "MappingContent{records=" + records + ", meta=" + meta + "}"; }
    }

    /** Meta definition returned by {@code bg.goods.sizecharts.meta.get}. */
    public static class SizeSpecMeta {
        /** Size group definitions used in {@code content.meta.groupList}. */
        private List<MetaGroup> groupList;
        /** Measurement element definitions used in {@code content.meta.elementList}. */
        private List<MetaElement> elementList;
        public List<MetaGroup> getGroupList() { return groupList; }
        public List<MetaElement> getElementList() { return elementList; }
    }

    /** Single size group definition. */
    public static class MetaGroup {
        /** Display name, for example {@code 尺码}, {@code 美码}. */
        private String name;
        /** Group id used in records.values and as {@code generalSizeType}. */
        private Integer id;
        /** Whether this group is non-required. Default is required when false/null. */
        private Boolean unnecessary;
        public String getName() { return name; }
        public Integer getId() { return id; }
        public Boolean getUnnecessary() { return unnecessary; }
        @Override public String toString() { return "MetaGroup{id=" + id + ", name='" + name + "', unnecessary=" + unnecessary + "}"; }
    }

    public static class MetaElement {
        /** Display name, for example {@code 衣长}, {@code 胸围全围}. */
        private String name;
        /** Element id used as a key in records.values. */
        private Integer id;
        /** Whether this measurement element is required. Default is optional when false/null. */
        private Boolean necessary;
        public String getName() { return name; }
        public Integer getId() { return id; }
        public Boolean getNecessary() { return necessary; }
        @Override public String toString() { return "MetaElement{id=" + id + ", name='" + name + "', necessary=" + necessary + "}"; }
    }

    /** Wrapper for {@code bg.goods.sizecharts.get}. */
    public static class SizeChartListResult {
        /** Current page offset. */
        private Integer offset;
        /** Current page size. */
        private Integer pageSize;
        /** Total count of templates. */
        private Integer totalCount;
        /** Listed size chart templates. */
        private List<SizeSpecData> sizeSpecDataList;
        public Integer getOffset() { return offset; }
        public Integer getPageSize() { return pageSize; }
        public Integer getTotalCount() { return totalCount; }
        public List<SizeSpecData> getSizeSpecDataList() { return sizeSpecDataList; }
    }

    /** Single size chart template row from {@code bg.goods.sizecharts.get}. */
    public static class SizeSpecData {
        /** Size class id bound to the template. */
        private Integer classId;
        /** Base template business id. */
        private Long businessId;
        /** Template display name. */
        private String name;
        /** Whether the template can be reused directly. */
        private Boolean reusable;
        /** Last update timestamp. */
        private Long updatedAt;
        public Integer getClassId() { return classId; }
        public Long getBusinessId() { return businessId; }
        public void setBusinessId(Long businessId) { this.businessId = businessId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public void setClassId(Integer classId) { this.classId = classId; }
        public Boolean getReusable() { return reusable; }
        public Long getUpdatedAt() { return updatedAt; }
    }

    /** Wrapper for {@code bg.goods.sizecharts.template.create}. */
    public static class SizeChartTemplateCreateResult {
        /** Temporary template id used for actual publish-time sizeTemplateId. */
        private Long tempBusinessId;
        public Long getTempBusinessId() { return tempBusinessId; }
        public void setTempBusinessId(Long tempBusinessId) { this.tempBusinessId = tempBusinessId; }
    }

    /** Wrapper for {@code bg.goods.sizecharts.create}. */
    public static class SizeChartCreateResult {
        /** Base template id created successfully. */
        private Long businessId;
        public Long getBusinessId() { return businessId; }
        public void setBusinessId(Long businessId) { this.businessId = businessId; }
    }
}
