package com.tminos.temu.upin.sdk.v2.category;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tminos.temu.upin.sdk.v2.common.CategoriesResponse;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.util.HttpClient;
import com.tminos.temu.upin.sdk.v2.util.JsonUtil;
import com.tminos.temu.upin.sdk.v2.util.SignatureUtil;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryApiClient {

    public static final String DATA_TYPE = "JSON";
    public static final String VERSION = "V1";

    public static final String API_CATS_GET = "bg.goods.cats.get";
    public static final String API_ATTRS_GET = "bg.goods.attrs.get";
    public static final String API_PARENTSPEC_GET = "bg.glo.goods.parentspec.get";
    public static final String API_SPEC_CREATE = "bg.glo.goods.spec.create";
    public static final String API_CATSMANDATORY_GET = "bg.glo.goods.catsmandatory.get";
    public static final String API_CATEGORY_MATCH = "bg.goods.category.match";
    public static final String API_CATEGORY_MAPPING = "bg.goods.category.mapping";
    public static final String API_PHOTO_CATEGORY_GET = "bg.goods.photorecommendationcategory.get";
    public static final String API_ATTRIBUTE_MAPPING = "bg.goods.attribute.mapping";

    private final TemuOpenApiCredentials creds;

    public CategoryApiClient(TemuOpenApiCredentials creds) {
        if (creds == null) throw new IllegalArgumentException("creds is required");
        if (isBlank(creds.getAccessToken())) throw new IllegalArgumentException("accessToken is required");
        if (isBlank(creds.getAppKey())) throw new IllegalArgumentException("appKey is required");
        if (isBlank(creds.getAppSecret())) throw new IllegalArgumentException("appSecret is required");
        this.creds = creds;
    }

    public CategoriesResponse getCategories(Integer siteId, Long parentCatId, Boolean showHidden) throws Exception {
        Map<String, Object> params = baseParams(API_CATS_GET);
        if (siteId != null) params.put("siteId", siteId);
        if (parentCatId != null) params.put("parentCatId", parentCatId);
        if (showHidden != null) params.put("showHidden", showHidden);

        String resp = postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
        return new Gson().fromJson(resp, CategoriesResponse.class);
    }

    public String getCategoryAttributes(Integer catId) throws Exception {
        Map<String, Object> params = baseParams(API_ATTRS_GET);
        params.put("catId", catId);
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL_PA, params);
    }

    public TemuApiResponse<CategoryAttributesResult> getCategoryAttributesResult(Integer catId) throws Exception {
        Map<String, Object> params = baseParams(API_ATTRS_GET);
        params.put("catId", catId);
        return post(TemuOpenApiEndpoints.API_BASE_URL_PA, params, CategoryAttributesResult.class);
    }

    public String getParentSpecList() throws Exception {
        Map<String, Object> params = baseParams(API_PARENTSPEC_GET);
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
    }

    public String createSpec(Integer parentSpecId, String specName) throws Exception {
        Map<String, Object> params = baseParams(API_SPEC_CREATE);
        params.put("parentSpecId", parentSpecId);
        params.put("specName", specName);
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
    }

    public String getCategoryMandatory(Long leafCatId, List<Integer> configItems, List<Map<String, Object>> productPropertyReqs) throws Exception {
        Map<String, Object> params = baseParams(API_CATSMANDATORY_GET);
        params.put("leafCatId", leafCatId);
        if (configItems != null && !configItems.isEmpty()) {
            params.put("configItems", configItems);
        }
        if (productPropertyReqs != null && !productPropertyReqs.isEmpty()) {
            params.put("productPropertyReqs", productPropertyReqs);
        }
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
    }

    public TemuApiResponse<CategoryMandatoryResult> getCategoryMandatory(CategoryMandatoryRequest request) throws Exception {
        Map<String, Object> params = baseParams(API_CATSMANDATORY_GET);
        if (request != null) {
            if (request.getLeafCatId() != null) {
                params.put("leafCatId", request.getLeafCatId());
            }
            if (request.getConfigItems() != null && !request.getConfigItems().isEmpty()) {
                params.put("configItems", request.getConfigItems());
            }
            if (request.getProductPropertyReqs() != null && !request.getProductPropertyReqs().isEmpty()) {
                Gson gson = new Gson();
                Type mapType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> propMaps = gson.fromJson(gson.toJson(request.getProductPropertyReqs()), mapType);
                params.put("productPropertyReqs", propMaps);
            }
        }
        return post(TemuOpenApiEndpoints.API_BASE_URL, params, CategoryMandatoryResult.class);
    }

    public String matchCategory(String searchText) throws Exception {
        Map<String, Object> params = baseParams(API_CATEGORY_MATCH);
        params.put("searchText", searchText);
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL_PA, params);
    }

    public String mapCategoryByTitle(
            Integer catId,
            Integer cat1Id,
            Integer cat2Id,
            Integer cat3Id,
            Integer cat4Id,
            String catName,
            String cat1Name,
            String cat2Name,
            String cat3Name,
            String cat4Name,
            String goodsName,
            String goodsNameEn
    ) throws Exception {
        Map<String, Object> params = baseParams(API_CATEGORY_MAPPING);
        if (catId != null) params.put("catId", catId);
        if (cat1Id != null) params.put("cat1Id", cat1Id);
        if (cat2Id != null) params.put("cat2Id", cat2Id);
        if (cat3Id != null) params.put("cat3Id", cat3Id);
        if (cat4Id != null) params.put("cat4Id", cat4Id);
        if (catName != null) params.put("catName", catName);
        if (cat1Name != null) params.put("cat1Name", cat1Name);
        if (cat2Name != null) params.put("cat2Name", cat2Name);
        if (cat3Name != null) params.put("cat3Name", cat3Name);
        if (cat4Name != null) params.put("cat4Name", cat4Name);
        if (goodsName != null) params.put("goodsName", goodsName);
        if (goodsNameEn != null) params.put("goodsNameEn", goodsNameEn);
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
    }

    public String photoRecommendationCategory(
            List<String> inputMainUrls,
            List<String> inputSideUrls,
            String goodsTitle,
            String goodsDescription,
            String goodsBrand,
            String goodsId
    ) throws Exception {
        Map<String, Object> params = baseParams(API_PHOTO_CATEGORY_GET);
        params.put("inputMainUrls", inputMainUrls);
        if (inputSideUrls != null && !inputSideUrls.isEmpty()) {
            params.put("inputSideUrls", inputSideUrls);
        }
        if (goodsTitle != null) params.put("goodsTitle", goodsTitle);
        if (goodsDescription != null) params.put("goodsDescription", goodsDescription);
        if (goodsBrand != null) params.put("goodsBrand", goodsBrand);
        if (goodsId != null) {
            Map<String, Object> param = new HashMap<>();
            param.put("goodsId", goodsId);
            params.put("param", param);
        }
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
    }

    public String attributeMapping(
            String catId,
            List<Map<String, Object>> goodsProp,
            String goodsName,
            String catName,
            String goodsId,
            String mainImageUrl
    ) throws Exception {
        Map<String, Object> params = baseParams(API_ATTRIBUTE_MAPPING);
        params.put("catId", catId);
        params.put("goodsProp", goodsProp);
        params.put("goodsName", goodsName);
        if (catName != null) params.put("catName", catName);
        if (goodsId != null) params.put("goodsId", goodsId);
        if (mainImageUrl != null) params.put("mainImageUrl", mainImageUrl);
        return postRaw(TemuOpenApiEndpoints.API_BASE_URL, params);
    }

    private String postRaw(String routerUrl, Map<String, Object> params) throws Exception {
        if (isBlank(routerUrl)) throw new IllegalArgumentException("routerUrl is required");
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);
        return HttpClient.sendPostRequest(routerUrl.trim(), json);
    }

    private <T> TemuApiResponse<T> post(String routerUrl, Map<String, Object> params, Class<T> resultType) throws Exception {
        if (isBlank(routerUrl)) throw new IllegalArgumentException("routerUrl is required");
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);
        return parseResponse(HttpClient.sendPostRequest(routerUrl.trim(), json), resultType);
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
            Object errorCode = probe.get("errorCode");
            if (errorCode instanceof Number n) {
                wrapper.setErrorCode(n.intValue());
                if (n.intValue() == 1000000) {
                    wrapper.setSuccess(true);
                }
            }
            if (!wrapper.isSuccess()) {
                Object success = probe.get("success");
                wrapper.setSuccess(Boolean.TRUE.equals(success));
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

    public static Map<String, Object> createProductPropertyReq(
            Integer vid,
            String valueUnit,
            Integer pid,
            Integer templatePid,
            String numberInputValue,
            String propValue,
            String propName,
            Integer refPid
    ) {
        Map<String, Object> item = new HashMap<>();
        item.put("vid", vid == null ? 0 : vid);
        item.put("valueUnit", valueUnit == null ? "" : valueUnit);
        item.put("pid", pid);
        item.put("templatePid", templatePid);
        if (numberInputValue != null) item.put("numberInputValue", numberInputValue);
        item.put("propValue", propValue);
        item.put("propName", propName);
        item.put("refPid", refPid);
        return item;
    }

    public static Map<String, Object> createGoodsProp(String propName, List<String> values) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("propName", propName);
        prop.put("values", values == null ? new ArrayList<>() : values);
        return prop;
    }
}
