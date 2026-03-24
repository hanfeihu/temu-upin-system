package com.tminos.temu.upin.sdk.v2.goods;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsResponse;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.util.HttpClient;
import com.tminos.temu.upin.sdk.v2.util.JsonUtil;
import com.tminos.temu.upin.sdk.v2.util.SignatureUtil;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemuGloGoodsV2Client {

    public static final String DATA_TYPE = "JSON";
    public static final String VERSION = "V1";

    public static final String API_ADD_GLO_GOODS = "bg.glo.goods.add";

    private final TemuOpenApiCredentials creds;

    public TemuGloGoodsV2Client(TemuOpenApiCredentials creds) {
        if (creds == null) throw new IllegalArgumentException("creds is required");
        if (isBlank(creds.getAccessToken())) throw new IllegalArgumentException("accessToken is required");
        if (isBlank(creds.getAppKey())) throw new IllegalArgumentException("appKey is required");
        if (isBlank(creds.getAppSecret())) throw new IllegalArgumentException("appSecret is required");
        this.creds = creds;
    }

    public String addGloGoodsRaw(Map<String, Object> requestBody) throws Exception {
        Map<String, Object> params = baseParams(API_ADD_GLO_GOODS);
        if (requestBody != null) {
            for (Map.Entry<String, Object> e : requestBody.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                if (isReservedKey(k)) continue;
                params.put(k, e.getValue());
            }
        }
        return postRaw(params);
    }

    public String addGloGoodsRaw(Object request) throws Exception {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> body = request == null ? null : gson.fromJson(gson.toJson(request), mapType);
        return addGloGoodsRaw(body);
    }

    public String addGloGoodsRaw(AddGloGoodsRequest request) throws Exception {
        return addGloGoodsRaw((Object) request);
    }


    public <T> TemuApiResponse<T> addGloGoods(Map<String, Object> requestBody, Class<T> resultType) throws Exception {
        return post(mergeIntoBase(requestBody), resultType);
    }

    public TemuApiResponse<AddGloGoodsResponse> addGloGoods(AddGloGoodsRequest request) throws Exception {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> body = request == null ? null : gson.fromJson(gson.toJson(request), mapType);
        return addGloGoods(body, AddGloGoodsResponse.class);
    }

    private Map<String, Object> mergeIntoBase(Map<String, Object> requestBody) {
        Map<String, Object> params = baseParams(API_ADD_GLO_GOODS);
        if (requestBody != null) {
            for (Map.Entry<String, Object> e : requestBody.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                if (isReservedKey(k)) continue;
                params.put(k, e.getValue());
            }
        }
        return params;
    }

    private static boolean isReservedKey(String k) {
        return "type".equals(k)
                || "app_key".equals(k)
                || "access_token".equals(k)
                || "timestamp".equals(k)
                || "data_type".equals(k)
                || "version".equals(k)
                || "sign".equals(k);
    }

    private <T> TemuApiResponse<T> post(Map<String, Object> params, Class<T> resultType) throws Exception {
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);

        TemuApiResponse<T> second = parseResponse(HttpClient.sendPostRequest(TemuOpenApiEndpoints.API_BASE_URL, json), resultType);
        return second;
    }

    private String postRaw(Map<String, Object> params) throws Exception {
        sanitizeParams(params);
        params.put("sign", SignatureUtil.generateSignature(params, creds.getAppSecret()));
        String json = JsonUtil.toJson(params);

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
}
