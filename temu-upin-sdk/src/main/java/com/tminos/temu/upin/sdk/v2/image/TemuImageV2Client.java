package com.tminos.temu.upin.sdk.v2.image;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.util.HttpClient;
import com.tminos.temu.upin.sdk.v2.util.JsonUtil;
import com.tminos.temu.upin.sdk.v2.util.SignatureUtil;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemuImageV2Client {

    public static final String DATA_TYPE = "JSON";
    public static final String VERSION = "V1";

    public static final String API_UPLOAD_GLOBAL = "bg.goods.image.upload.global";
    public static final String API_TRANSLATE_GLOBAL = "bg.algo.image.translate.global";
    public static final String API_TRANSLATE_RESULT_GLOBAL = "bg.algo.image.translate.result.global";
    public static final String API_CM2IN = "bg.glo.fancy.image.cm2in";
    public static final String API_PICTURE_COMPRESSION = "bg.glo.picturecompression.get";
    public static final String API_TEXT_TO_PICTURE_GLOBAL = "bg.goods.texttopicture.add.global";

    private final TemuOpenApiCredentials creds;

    public TemuImageV2Client(TemuOpenApiCredentials creds) {
        if (creds == null) throw new IllegalArgumentException("creds is required");
        if (isBlank(creds.getAccessToken())) throw new IllegalArgumentException("accessToken is required");
        if (isBlank(creds.getAppKey())) throw new IllegalArgumentException("appKey is required");
        if (isBlank(creds.getAppSecret())) throw new IllegalArgumentException("appSecret is required");
        this.creds = creds;
    }

    public String uploadGlobalImageBase64Raw(String base64Image, Integer imageBizType, Map<String, Object> options) throws Exception {
        if (isBlank(base64Image)) throw new IllegalArgumentException("base64Image is required");
        Map<String, Object> params = baseParams(API_UPLOAD_GLOBAL);
        params.put("image", base64Image);
        if (imageBizType != null) {
            params.put("imageBizType", imageBizType);
        }
        if (options != null && !options.isEmpty()) {
            params.put("options", options);
        }
        return postRaw(params);
    }

    public TemuApiResponse<Map<String, Object>> uploadGlobalImageBase64(String base64Image, Integer imageBizType, Map<String, Object> options) throws Exception {
        String raw = uploadGlobalImageBase64Raw(base64Image, imageBizType, options);
        return parseMapResponse(raw);
    }

    public String uploadGlobalImageByUrlRaw(String imageUrl, Integer imageBizType, Map<String, Object> options) throws Exception {
        if (isBlank(imageUrl)) throw new IllegalArgumentException("imageUrl is required");
        String u = imageUrl.trim();
        if (u.startsWith("data:")) {
            return uploadGlobalImageBase64Raw(u, imageBizType, options);
        }
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(u))
                .timeout(Duration.ofSeconds(90))
                .GET()
                .build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Download failed: HTTP " + resp.statusCode());
        }
        String contentType = resp.headers().firstValue("content-type").orElse(null);
        String mime = normalizeImageMimeType(contentType, resp.body());
        String base64 = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(resp.body());
        return uploadGlobalImageBase64Raw(base64, imageBizType, options);
    }

    public String translateGlobalImageRaw(ImageTranslateRequest request) throws Exception {
        validateTranslateRequest(request);
        Map<String, Object> params = baseParams(API_TRANSLATE_GLOBAL);
        params.put("targetLang", request.getTargetLang());
        params.put("imageUrl", request.getImageUrl());
        params.put("customTaskId", request.getCustomTaskId());
        params.put("isContainDetail", request.getIsContainDetail());
        params.put("language", request.getLanguage());
        if (!isBlank(request.getScene())) {
            params.put("scene", request.getScene());
        }
        return postRaw(params);
    }

    public TemuApiResponse<ImageTranslateSubmitResult> translateGlobalImage(ImageTranslateRequest request) throws Exception {
        return parseResponse(translateGlobalImageRaw(request), ImageTranslateSubmitResult.class);
    }

    public String getTranslateGlobalImageResultRaw(String taskId) throws Exception {
        if (isBlank(taskId)) throw new IllegalArgumentException("taskId is required");
        Map<String, Object> params = baseParams(API_TRANSLATE_RESULT_GLOBAL);
        params.put("taskId", taskId);
        return postRaw(params);
    }

    public TemuApiResponse<ImageTranslateQueryResult> getTranslateGlobalImageResult(String taskId) throws Exception {
        return parseResponse(getTranslateGlobalImageResultRaw(taskId), ImageTranslateQueryResult.class);
    }

    public String convertCmToInchRaw(String imageUrl) throws Exception {
        if (isBlank(imageUrl)) throw new IllegalArgumentException("imageUrl is required");
        Map<String, Object> params = baseParams(API_CM2IN);
        params.put("imageUrl", imageUrl);
        return postRaw(params);
    }

    public String compressGlobalPicturesRaw(List<String> urls) throws Exception {
        Map<String, Object> params = baseParams(API_PICTURE_COMPRESSION);
        if (urls != null && !urls.isEmpty()) {
            params.put("urls", urls);
        }
        return postRaw(params);
    }

    public String textToPictureGlobalRaw(TextToPictureRequest request) throws Exception {
        validateTextToPictureRequest(request);
        Map<String, Object> params = baseParams(API_TEXT_TO_PICTURE_GLOBAL);
        params.put("backColor", request.getBackColor());
        params.put("text", request.getText());
        params.put("align", request.getAlign());
        params.put("fontColor", request.getFontColor());
        params.put("font", request.getFont());
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

    public static <T> TemuApiResponse<T> parseResponse(String resp, Class<T> resultType) {
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
    private static TemuApiResponse<Map<String, Object>> parseMapResponse(String resp) {
        TemuApiResponse<Map<String, Object>> wrapper = new TemuApiResponse<>();
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
            if (result instanceof Map<?, ?> map) {
                wrapper.setResult((Map<String, Object>) map);
            } else if (result != null) {
                wrapper.setResult(gson.fromJson(gson.toJson(result), mapType));
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

    private static void validateTranslateRequest(ImageTranslateRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (isBlank(request.getTargetLang())) throw new IllegalArgumentException("targetLang is required");
        if (isBlank(request.getImageUrl())) throw new IllegalArgumentException("imageUrl is required");
        if (request.getCustomTaskId() == null) throw new IllegalArgumentException("customTaskId is required");
        if (request.getIsContainDetail() == null) throw new IllegalArgumentException("isContainDetail is required");
        if (isBlank(request.getLanguage())) throw new IllegalArgumentException("language is required");
    }

    private static void validateTextToPictureRequest(TextToPictureRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (isBlank(request.getBackColor())) throw new IllegalArgumentException("backColor is required");
        if (isBlank(request.getText())) throw new IllegalArgumentException("text is required");
        if (isBlank(request.getAlign())) throw new IllegalArgumentException("align is required");
        if (isBlank(request.getFontColor())) throw new IllegalArgumentException("fontColor is required");
        if (isBlank(request.getFont())) throw new IllegalArgumentException("font is required");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizeImageMimeType(String contentType, byte[] bytes) {
        String ct = contentType;
        if (ct != null) {
            int semi = ct.indexOf(';');
            if (semi >= 0) ct = ct.substring(0, semi);
            ct = ct.trim().toLowerCase();
            if (ct.startsWith("image/")) {
                return ct;
            }
        }
        String inferred = inferImageMimeType(bytes);
        return inferred == null ? "image/jpeg" : inferred;
    }

    private static String inferImageMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return null;
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        if (b0 == 0xFF && b1 == 0xD8) return "image/jpeg";
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "image/png";
        if (b0 == 'G' && b1 == 'I' && b2 == 'F') return "image/gif";
        if (b0 == 'B' && b1 == 'M') return "image/bmp";
        if (b0 == 'R' && b1 == 'I' && b2 == 'F' && b3 == 'F') {
            int b8 = bytes[8] & 0xFF;
            int b9 = bytes[9] & 0xFF;
            int b10 = bytes[10] & 0xFF;
            int b11 = bytes[11] & 0xFF;
            if (b8 == 'W' && b9 == 'E' && b10 == 'B' && b11 == 'P') return "image/webp";
        }
        return null;
    }

    public static class ImageTranslateRequest {
        private String targetLang;
        private String imageUrl;
        private Long customTaskId;
        private Boolean isContainDetail;
        private String language;
        private String scene;

        public String getTargetLang() { return targetLang; }
        public void setTargetLang(String targetLang) { this.targetLang = targetLang; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public Long getCustomTaskId() { return customTaskId; }
        public void setCustomTaskId(Long customTaskId) { this.customTaskId = customTaskId; }
        public Boolean getIsContainDetail() { return isContainDetail; }
        public void setIsContainDetail(Boolean isContainDetail) { this.isContainDetail = isContainDetail; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
    }

    public static class ImageTranslateSubmitResult {
        private Integer resultCode;
        private String resultMsg;
        private String taskId;

        public Integer getResultCode() { return resultCode; }
        public void setResultCode(Integer resultCode) { this.resultCode = resultCode; }
        public String getResultMsg() { return resultMsg; }
        public void setResultMsg(String resultMsg) { this.resultMsg = resultMsg; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
    }

    public static class ImageTranslateQueryResult {
        private String imageResultUrl;
        private String imageUrl;

        public String getImageResultUrl() { return imageResultUrl; }
        public void setImageResultUrl(String imageResultUrl) { this.imageResultUrl = imageResultUrl; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    public static class TextToPictureRequest {
        private String backColor;
        private String text;
        private String align;
        private String fontColor;
        private String font;

        public String getBackColor() { return backColor; }
        public void setBackColor(String backColor) { this.backColor = backColor; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getAlign() { return align; }
        public void setAlign(String align) { this.align = align; }
        public String getFontColor() { return fontColor; }
        public void setFontColor(String fontColor) { this.fontColor = fontColor; }
        public String getFont() { return font; }
        public void setFont(String font) { this.font = font; }
    }
}
