package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DianxiaomiPackageClient {

    private static final String LIST_PACKAGE_URL = "https://www.dianxiaomi.com/api/package/list.json";
    private static final String SEARCH_PACKAGE_URL = "https://www.dianxiaomi.com/api/package/searchPackage.json";
    private static final String ORDER_REFERER = "https://www.dianxiaomi.com/web/order/all?go=m1-1";
    private static final int MAX_BUSY_RETRY_ATTEMPTS = 3;
    private static final long BUSY_RETRY_BACKOFF_MS = 600L;
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;

    public DianxiaomiPackageClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchResponse searchByParentOrderSn(String cookie, String parentOrderSn) {
        if (!StringUtils.hasText(cookie)) {
            throw new IllegalArgumentException("店小秘 cookie 为空");
        }
        if (!StringUtils.hasText(parentOrderSn)) {
            throw new IllegalArgumentException("父订单号不能为空");
        }
        String normalizedCookie = cookie.trim();
        String normalizedParentOrderSn = parentOrderSn.trim();
        IllegalStateException lastBusyException = null;
        for (int attempt = 1; attempt <= MAX_BUSY_RETRY_ATTEMPTS; attempt++) {
            try {
                return doSearch(normalizedCookie, normalizedParentOrderSn);
            } catch (IllegalStateException e) {
                if (!isBusyResponse(e) || attempt >= MAX_BUSY_RETRY_ATTEMPTS) {
                    throw e;
                }
                lastBusyException = e;
                sleepQuietly(BUSY_RETRY_BACKOFF_MS * attempt);
            }
        }
        throw lastBusyException == null ? new IllegalStateException("店小秘包裹查询失败: 未知错误") : lastBusyException;
    }

    public ListResponse listByShopId(String cookie, String dianxiaomiShopId, int pageNo, int pageSize) {
        if (!StringUtils.hasText(cookie)) {
            throw new IllegalArgumentException("店小秘 cookie 为空");
        }
        if (!StringUtils.hasText(dianxiaomiShopId)) {
            throw new IllegalArgumentException("店小秘店铺ID为空");
        }
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        try {
            RestTemplate restTemplate = new RestTemplate(buildFactory());
            restTemplate.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
            restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

            HttpHeaders headers = buildHeaders(cookie.trim());
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(
                    buildListForm(dianxiaomiShopId.trim(), safePageNo, safePageSize),
                    headers
            );
            ResponseEntity<String> response = restTemplate.exchange(
                    LIST_PACKAGE_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode().value());
            }
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("店小秘响应为空");
            }

            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String message = trim(root.path("msg").asText(null));
                throw new IllegalStateException(StringUtils.hasText(message) ? message : "店小秘接口返回失败");
            }

            JsonNode page = root.path("data").path("page");
            JsonNode list = page.path("list");
            return new ListResponse(
                    list.isArray() ? list : objectMapper.createArrayNode(),
                    page.path("totalSize").asInt(0),
                    page.path("totalPage").asInt(0),
                    page.path("pageNo").asInt(safePageNo),
                    page.path("pageSize").asInt(safePageSize),
                    body
            );
        } catch (Exception e) {
            throw new IllegalStateException("店小秘订单列表查询失败: " + e.getMessage(), e);
        }
    }

    private SearchResponse doSearch(String cookie, String parentOrderSn) {
        try {
            RestTemplate restTemplate = new RestTemplate(buildFactory());
            restTemplate.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
            restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

            HttpHeaders headers = buildHeaders(cookie);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(buildForm(parentOrderSn), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    SEARCH_PACKAGE_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode().value());
            }
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("店小秘响应为空");
            }

            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String message = trim(root.path("msg").asText(null));
                throw new IllegalStateException(StringUtils.hasText(message) ? message : "店小秘接口返回失败");
            }

            List<SearchItem> items = new ArrayList<>();
            JsonNode list = root.path("data").path("page").path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    items.add(new SearchItem(
                            trim(item.path("packageNumber").asText(null)),
                            trim(item.path("orderId").asText(null)),
                            trim(item.path("id").asText(null)),
                            trim(item.path("shopId").asText(null))
                    ));
                }
            }
            return new SearchResponse(items, body);
        } catch (Exception e) {
            throw new IllegalStateException("店小秘包裹查询失败: " + e.getMessage(), e);
        }
    }

    private static boolean isBusyResponse(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = trim(current.getMessage());
            if (StringUtils.hasText(message) && message.contains("系统繁忙")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static MultiValueMap<String, String> buildForm(String parentOrderSn) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("pageNo", "1");
        form.add("pageSize", "100");
        form.add("state", "");
        form.add("shopId", "-1");
        form.add("history", "");
        form.add("searchType", "orderId");
        form.add("content", parentOrderSn);
        form.add("isVoided", "-1");
        form.add("isRemoved", "-1");
        form.add("commitPlatform", "");
        form.add("platform", "");
        form.add("isGreen", "0");
        form.add("isYellow", "0");
        form.add("isOrange", "0");
        form.add("isRed", "0");
        form.add("isViolet", "0");
        form.add("isBlue", "0");
        form.add("cornflowerBlue", "0");
        form.add("pink", "0");
        form.add("teal", "0");
        form.add("turquoise", "0");
        form.add("unmarked", "0");
        form.add("isSearch", "1");
        form.add("isFree", "-1");
        form.add("isBatch", "-1");
        form.add("isOversea", "-1");
        form.add("forbiddenStatus", "-1");
        form.add("forbiddenReason", "0");
        form.add("orderField", "order_create_time");
        form.add("behindTrack", "-1");
        form.add("storageId", "0");
        form.add("timeOut", "0");
        form.add("orderSearchType", "1");
        form.add("axios_cancelToken", "true");
        return form;
    }

    private static HttpHeaders buildHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set(HttpHeaders.ORIGIN, "https://www.dianxiaomi.com");
        headers.set(HttpHeaders.REFERER, ORDER_REFERER);
        headers.set(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT);
        headers.set("bx-v", "2.5.11");
        return headers;
    }

    private static MultiValueMap<String, String> buildListForm(String dianxiaomiShopId, int pageNo, int pageSize) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("pageNo", String.valueOf(pageNo));
        form.add("pageSize", String.valueOf(pageSize));
        form.add("shopId", dianxiaomiShopId);
        form.add("state", "");
        form.add("platform", "");
        form.add("isSearch", "1");
        form.add("searchType", "orderId");
        form.add("authId", "-1");
        form.add("startTime", "");
        form.add("endTime", "");
        form.add("country", "");
        form.add("orderField", "order_create_time");
        form.add("isVoided", "-1");
        form.add("isRemoved", "-1");
        form.add("ruleId", "-1");
        form.add("sysRule", "");
        form.add("applyType", "");
        form.add("applyStatus", "");
        form.add("printJh", "-1");
        form.add("printMd", "-1");
        form.add("commitPlatform", "");
        form.add("productStatus", "");
        form.add("jhComment", "-1");
        form.add("storageId", "0");
        form.add("isOversea", "-1");
        form.add("isFree", "-1");
        form.add("isBatch", "-1");
        form.add("history", "");
        form.add("custom", "-1");
        form.add("timeOut", "0");
        form.add("refundStatus", "0");
        form.add("buyerAccount", "");
        form.add("forbiddenStatus", "-1");
        form.add("forbiddenReason", "0");
        form.add("behindTrack", "-1");
        form.add("orderId", "");
        form.add("axios_cancelToken", "true");
        return form;
    }

    private static SimpleClientHttpRequestFactory buildFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        return factory;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public record SearchResponse(List<SearchItem> items, String rawJson) {
    }

    public record SearchItem(String packageNumber, String orderId, String id, String shopId) {
    }

    public record ListResponse(JsonNode list,
                               int totalSize,
                               int totalPage,
                               int pageNo,
                               int pageSize,
                               String rawJson) {
    }
}
