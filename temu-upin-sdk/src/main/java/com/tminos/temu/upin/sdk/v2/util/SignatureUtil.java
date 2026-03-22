package com.tminos.temu.upin.sdk.v2.util;

import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Temu OpenAPI signature helper.
 * Copied from legacy openapi module; keep behavior stable.
 */
public class SignatureUtil {

    public static String generateSignature(Map<String, Object> params, String appSecret) {
        try {
            TreeMap<String, Object> sortedParams = new TreeMap<>(params);

            StringBuilder signString = new StringBuilder();
            signString.append(appSecret);

            for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if ("sign".equals(key)) continue;
                if (value == null) continue;

                String valueStr;
                if (value instanceof Map || value instanceof java.util.List) {
                    valueStr = JsonUtil.toJson(value);
                } else {
                    valueStr = value.toString();
                }

                signString.append(key).append(valueStr);
            }

            signString.append(appSecret);
            return md5(signString.toString()).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }
}
