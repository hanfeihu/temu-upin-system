package com.tminos.temu.upin.sdk.v2.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import com.tminos.temu.upin.sdk.v2.util.HttpClient;
import com.tminos.temu.upin.sdk.v2.util.JsonUtil;
import com.tminos.temu.upin.sdk.v2.util.SignatureUtil;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 TEMU OpenAPI 调用客户端。
 * <p>
 * 提供统一的 API 调用、响应解析、分页遍历能力，供各业务模块复用。
 * 与 SDK 中已有的特定领域 Client（如 TemuGloGoodsV2Client）互补：
 * 领域 Client 提供强类型封装，本客户端适用于灵活调用场景。
 */
public class TemuOpenApiClient {

    private static final String DATA_TYPE = "JSON";
    private static final String VERSION = "V1";

    // ==================== 商品相关 API ====================
    public static final String API_GOODS_LIST = "bg.glo.goods.list.get";
    public static final String API_GOODS_DETAIL = "bg.glo.goods.detail.get";
    public static final String API_PRODUCT_SEARCH = "bg.glo.product.search";
    public static final String API_GOODS_PRICE_LIST = "bg.glo.goods.price.list.get";
    public static final String API_LOGISTICS_TEMPLATE = "bg.glo.logistics.template.get";
    public static final String API_WAREHOUSE_LIST = "bg.btg.goods.stock.warehouse.list.get";
    public static final String API_SEMI_STOCK_QUANTITY_UPDATE = "bg.btg.goods.stock.quantity.update";
    public static final String API_VIRTUAL_INVENTORY_JIT_EDIT = "bg.virtualinventoryjit.edit";
    public static final String API_VIRTUAL_INVENTORY_QTG_EDIT = "bg.qtg.stock.virtualinventoryjit.edit";
    public static final String API_GOODS_EDIT_SENSITIVE_ATTR = "bg.glo.goods.edit.sensitive.attr";

    // ==================== 核价 / 调价 API ====================
    public static final String API_PRICE_ADJUST_QUERY = "bg.semi.adjust.price.page.query.order";
    public static final String API_PRICE_ADJUST_REVIEW = "bg.semi.adjust.price.batch.review.order";
    public static final String API_PRICE_REVIEW_QUERY = "bg.semi.price.review.page.query.order";
    public static final String API_PRICE_REVIEW_CONFIRM = "bg.semi.price.review.confirm.order";
    public static final String API_PRICE_REVIEW_REJECT = "bg.semi.price.review.reject.order";

    // ==================== 营销活动 API ====================
    public static final String API_ACTIVITY_LIST = "bg.marketing.activity.list.get.global";
    public static final String API_ACTIVITY_DETAIL = "bg.marketing.activity.detail.get.global";
    public static final String API_ACTIVITY_PRODUCT = "bg.marketing.activity.product.get.global";
    public static final String API_ACTIVITY_SESSION_LIST = "bg.marketing.activity.session.list.get.global";
    public static final String API_ACTIVITY_ENROLL_LIST = "bg.marketing.activity.enroll.list.get.global";
    public static final String API_ACTIVITY_ENROLL_SUBMIT = "bg.marketing.activity.enroll.submit.global";

    private final TemuOpenApiCredentials creds;
    private final Gson gson = new Gson();

    public TemuOpenApiClient(TemuOpenApiCredentials creds) {
        if (creds == null) throw new IllegalArgumentException("creds is required");
        this.creds = creds;
    }

    /**
     * 通用 API 调用，返回原始 JSON 字符串。
     */
    public String callApi(String apiType, Map<String, Object> bizParams) throws Exception {
        return callApi(apiType, bizParams, TemuOpenApiEndpoints.API_BASE_URL);
    }

    public String callApi(String apiType, Map<String, Object> bizParams, String routerUrl) throws Exception {
        return callApi(apiType, bizParams, routerUrl, true);
    }

    public String callApi(String apiType,
                          Map<String, Object> bizParams,
                          String routerUrl,
                          boolean includeMallId) throws Exception {
        Map<String, Object> params = baseParams(apiType, includeMallId);
        if (bizParams != null) {
            for (Map.Entry<String, Object> e : bizParams.entrySet()) {
                if (!isReservedKey(e.getKey())) {
                    params.put(e.getKey(), e.getValue());
                }
            }
        }
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);
        return HttpClient.sendPostRequest(routerUrl, json);
    }

    /**
     * 通用 API 调用，解析返回结果。
     */
    public ApiResult callApiParsed(String apiType, Map<String, Object> bizParams) throws Exception {
        String raw = callApi(apiType, bizParams);
        return parseResult(raw);
    }

    public ApiResult callApiParsed(String apiType, Map<String, Object> bizParams, String routerUrl) throws Exception {
        return callApiParsed(apiType, bizParams, routerUrl, true);
    }

    public ApiResult callApiParsed(String apiType,
                                   Map<String, Object> bizParams,
                                   String routerUrl,
                                   boolean includeMallId) throws Exception {
        String raw = callApi(apiType, bizParams, routerUrl, includeMallId);
        return parseResult(raw);
    }

    /**
     * 分页遍历 API，自动翻页。
     */
    public void callApiPaged(String apiType, Map<String, Object> bizParams, int pageSize, PageHandler handler) throws Exception {
        if (API_GOODS_PRICE_LIST.equals(apiType)) {
            throw new IllegalArgumentException(apiType + " 不是分页接口，必须使用 productSkuIds 批量查询");
        }

        int page = 1;
        String pageParamName = resolvePageParamName(apiType);
        while (true) {
            Map<String, Object> params = new HashMap<>(bizParams != null ? bizParams : Map.of());
            params.put(pageParamName, page);
            params.put("pageSize", pageSize);
            ApiResult result = callApiParsed(apiType, params);
            if (!result.success) {
                throw new RuntimeException("API " + apiType + " failed: " + result.errorMsg);
            }
            boolean hasMore = handler.handle(result, page);
            if (!hasMore) break;
            page++;
        }
    }

    private String resolvePageParamName(String apiType) {
        return switch (apiType) {
            case API_PRODUCT_SEARCH -> "pageNum";
            case API_PRICE_ADJUST_QUERY, API_PRICE_REVIEW_QUERY, API_ACTIVITY_ENROLL_LIST -> "pageNo";
            default -> "page";
        };
    }

    public ApiResult parseResult(String raw) {
        try {
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> map = gson.fromJson(raw, mapType);
            if (map == null) return new ApiResult(false, "Empty response", null, null);

            boolean success = false;
            Object errorCode = map.containsKey("errorCode") ? map.get("errorCode") : map.get("error_code");
            if (errorCode instanceof Number n && n.intValue() == 1000000) {
                success = true;
            }
            if (errorCode instanceof String s && "1000000".equals(s.trim())) {
                success = true;
            }
            Object successFlag = map.get("success");
            if (Boolean.TRUE.equals(successFlag)) success = true;

            String errorMsg = map.get("errorMsg") instanceof String s
                    ? s
                    : map.get("error_msg") instanceof String snake ? snake : null;
            Object result = map.get("result");

            return new ApiResult(success, errorMsg, result, raw);
        } catch (Exception e) {
            return new ApiResult(false, "Parse failed: " + e.getMessage(), null, raw);
        }
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> baseParams(String apiType, boolean includeMallId) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", apiType);
        m.put("app_key", creds.getAppKey());
        m.put("access_token", creds.getAccessToken());
        if (includeMallId && creds.getShopId() != null && !creds.getShopId().isBlank()) {
            m.put("mall_id", creds.getShopId());
        }
        m.put("data_type", DATA_TYPE);
        m.put("version", VERSION);
        m.put("timestamp", System.currentTimeMillis() / 1000);
        return m;
    }

    private static boolean isReservedKey(String k) {
        return "type".equals(k) || "app_key".equals(k) || "access_token".equals(k)
                || "timestamp".equals(k) || "data_type".equals(k) || "version".equals(k)
                || "sign".equals(k) || "mall_id".equals(k);
    }

    @SuppressWarnings("unchecked")
    private static void sanitizeParams(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Double d && d % 1 == 0) {
                entry.setValue(d.longValue());
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
            if (value instanceof Double d && d % 1 == 0) {
                list.set(i, d.longValue());
            } else if (value instanceof Map<?, ?> map) {
                sanitizeParams((Map<String, Object>) map);
            } else if (value instanceof List<?> child) {
                sanitizeList((List<Object>) child);
            }
        }
    }

    // ==================== API 结果封装 ====================

    public static class ApiResult {
        public final boolean success;
        public final String errorMsg;
        public final Object result;
        public final String raw;

        public ApiResult(boolean success, String errorMsg, Object result, String raw) {
            this.success = success;
            this.errorMsg = errorMsg;
            this.result = result;
            this.raw = raw;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> resultAsMap() {
            if (result instanceof Map) return (Map<String, Object>) result;
            return null;
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> resultAsList() {
            if (result instanceof List) return (List<Map<String, Object>>) result;
            return null;
        }
    }

    @FunctionalInterface
    public interface PageHandler {
        /**
         * 处理一页数据，返回是否还有更多页。
         */
        boolean handle(ApiResult result, int currentPage);
    }
}
