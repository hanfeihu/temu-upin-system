package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TemuOrderOpenApiService {

    public static final String API_ORDER_LIST = "bg.order.list.v2.get";
    public static final String API_ORDER_LIST_LEGACY = "bg.order.list.get";
    public static final String API_PARENT_AFTERSALES_LIST = "bg.aftersales.parentaftersales.list.get";
    private static final String ORDER_APP_ROUTER_URL = "https://erp.tminos.com/openapi/router";

    private final ObjectMapper objectMapper;

    public TemuOrderOpenApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode listOrders(TemuOpenApiCredentials creds,
                               int pageNumber,
                               int pageSize,
                               Integer parentOrderStatus,
                               Long updateAtStartSec,
                               Long updateAtEndSec) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageNumber", pageNumber);
        params.put("pageSize", pageSize);
        if (parentOrderStatus != null) {
            params.put("parentOrderStatus", parentOrderStatus);
        }
        if (updateAtStartSec != null) {
            params.put("updateAtStart", updateAtStartSec);
        }
        if (updateAtEndSec != null) {
            params.put("updateAtEnd", updateAtEndSec);
        }
        return callApiWithApiFallbacks(creds, params, API_ORDER_LIST, API_ORDER_LIST_LEGACY);
    }

    public JsonNode listParentAftersales(TemuOpenApiCredentials creds,
                                         int pageNo,
                                         int pageSize,
                                         Long createAtStartSec,
                                         Long createAtEndSec,
                                         Long updateAtStartSec,
                                         Long updateAtEndSec,
                                         Integer afterSalesStatusGroup) {
        return listParentAftersales(
                creds,
                pageNo,
                pageSize,
                createAtStartSec,
                createAtEndSec,
                updateAtStartSec,
                updateAtEndSec,
                afterSalesStatusGroup,
                null,
                null
        );
    }

    public JsonNode listParentAftersales(TemuOpenApiCredentials creds,
                                         int pageNo,
                                         int pageSize,
                                         Long createAtStartSec,
                                         Long createAtEndSec,
                                         Long updateAtStartSec,
                                         Long updateAtEndSec,
                                         Integer afterSalesStatusGroup,
                                         Collection<String> parentOrderSnList,
                                         Collection<String> parentAfterSalesSnList) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageNo", pageNo);
        params.put("pageSize", pageSize);
        if (createAtStartSec != null && createAtEndSec != null) {
            params.put("createAtStart", createAtStartSec);
            params.put("createAtEnd", createAtEndSec);
        }
        if (updateAtStartSec != null && updateAtEndSec != null) {
            params.put("updateAtStart", updateAtStartSec);
            params.put("updateAtEnd", updateAtEndSec);
        }
        if (afterSalesStatusGroup != null) {
            params.put("afterSalesStatusGroup", afterSalesStatusGroup);
        }
        if (parentOrderSnList != null && !parentOrderSnList.isEmpty()) {
            params.put("parentOrderSnList", parentOrderSnList);
        }
        if (parentAfterSalesSnList != null && !parentAfterSalesSnList.isEmpty()) {
            params.put("parentAfterSalesSnList", parentAfterSalesSnList);
        }
        return callApiWithApiFallbacks(creds, params, API_PARENT_AFTERSALES_LIST);
    }

    private JsonNode callApiOrThrow(TemuOpenApiCredentials creds,
                                    String apiType,
                                    Map<String, Object> params,
                                    String routerUrl) {
        try {
            TemuOpenApiClient client = new TemuOpenApiClient(creds);
            // Order/aftersales APIs follow the legacy client behavior: do not force mall_id,
            // otherwise some order tokens return compatibility/auth errors even though the
            // same credentials work in the legacy openapi module.
            TemuOpenApiClient.ApiResult result = client.callApiParsed(apiType, params, routerUrl, false);
            if (result == null || !result.success) {
                String error = result == null ? "empty response" : result.errorMsg;
                String rawPreview = result == null ? null : summarizeRaw(result.raw);
                String detail = error == null ? "未知错误" : error;
                if (rawPreview != null && !rawPreview.isBlank()) {
                    detail = detail + " | raw=" + rawPreview;
                }
                throw new IllegalStateException(apiType + " 调用失败: " + detail);
            }
            return objectMapper.readTree(result.raw);
        } catch (Exception e) {
            throw new IllegalStateException(apiType + " 调用异常: " + e.getMessage(), e);
        }
    }

    private JsonNode callApiWithApiFallbacks(TemuOpenApiCredentials creds,
                                             Map<String, Object> params,
                                             String... apiTypes) {
        IllegalStateException lastException = null;
        for (String apiType : apiTypes) {
            try {
                return callApiOrThrow(creds, apiType, params, ORDER_APP_ROUTER_URL);
            } catch (IllegalStateException ex) {
                lastException = ex;
                if (!containsRetryableCompatibilityError(ex)) {
                    throw ex;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("订单 API 调用失败: 未拿到有效响应");
    }

    private boolean containsRetryableCompatibilityError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("type not exists")
                        || normalized.contains("access_token not exists")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String summarizeRaw(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500) + "...";
    }
}
