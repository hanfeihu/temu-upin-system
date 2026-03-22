package com.tminos.temu.upin.sdk.v2.util;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON serializer used by signature & request payload building.
 * Copied from legacy openapi module; keep behavior stable.
 */
public class JsonUtil {

    public static String toJson(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();

        if (obj instanceof Map) return mapToJson((Map<?, ?>) obj);
        if (obj instanceof List) return listToJson((List<?>) obj);

        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String mapToJson(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return "{}";

        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(escapeJson(entry.getKey().toString())).append("\":");
            json.append(toJson(entry.getValue()));
        }
        json.append("}");
        return json.toString();
    }

    private static String listToJson(List<?> list) {
        if (list == null || list.isEmpty()) return "[]";

        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) json.append(",");
            first = false;
            json.append(toJson(item));
        }
        json.append("]");
        return json.toString();
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
