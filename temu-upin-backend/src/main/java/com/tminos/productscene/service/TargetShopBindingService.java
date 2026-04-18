package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class TargetShopBindingService {

    public record TargetShopBinding(List<String> shopIds, List<String> shopNames) {}

    private final TemuShopRepository temuShopRepository;
    private final ObjectMapper objectMapper;

    public TargetShopBindingService(TemuShopRepository temuShopRepository, ObjectMapper objectMapper) {
        this.temuShopRepository = temuShopRepository;
        this.objectMapper = objectMapper;
    }

    public TargetShopBinding resolve(List<String> requestedShopIds) {
        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        if (requestedShopIds != null) {
            for (String requestedShopId : requestedShopIds) {
                if (StringUtils.hasText(requestedShopId)) {
                    normalizedIds.add(requestedShopId.trim());
                }
            }
        }

        if (normalizedIds.isEmpty()) {
            return new TargetShopBinding(Collections.emptyList(), Collections.emptyList());
        }

        List<TemuShop> shops = temuShopRepository.findByShopIdIn(normalizedIds);
        Map<String, TemuShop> shopMap = new LinkedHashMap<>();
        for (TemuShop shop : shops) {
            if (shop != null && StringUtils.hasText(shop.getShopId())) {
                shopMap.put(shop.getShopId().trim(), shop);
            }
        }

        List<String> resolvedIds = new ArrayList<>();
        List<String> resolvedNames = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();
        for (String normalizedId : normalizedIds) {
            TemuShop shop = shopMap.get(normalizedId);
            if (shop == null) {
                missingIds.add(normalizedId);
                continue;
            }
            resolvedIds.add(normalizedId);
            resolvedNames.add(StringUtils.hasText(shop.getShopName()) ? shop.getShopName().trim() : normalizedId);
        }

        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException("以下店铺不存在: " + String.join(", ", missingIds));
        }

        return new TargetShopBinding(List.copyOf(resolvedIds), List.copyOf(resolvedNames));
    }

    public String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("序列化店铺信息失败", e);
        }
    }

    public List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<?> values = objectMapper.readValue(json, List.class);
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}
