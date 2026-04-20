package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuShopFreightTemplateOptionDTO;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.sync.entity.TemuFreightTemplate;
import com.tminos.productscene.sync.repository.TemuFreightTemplateRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuShopFreightTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemuShopFreightTemplateService.class);

    private final TemuShopService shopService;
    private final TemuOpenApiCredentialService credentialService;
    private final TemuFreightTemplateRepository freightTemplateRepository;

    public TemuShopFreightTemplateService(TemuShopService shopService,
                                          TemuOpenApiCredentialService credentialService,
                                          TemuFreightTemplateRepository freightTemplateRepository) {
        this.shopService = shopService;
        this.credentialService = credentialService;
        this.freightTemplateRepository = freightTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<TemuShopFreightTemplateOptionDTO> listByShopRecordId(Long shopRecordId) {
        TemuShop shop = shopService.getByIdOrThrow(shopRecordId);
        LinkedHashMap<String, TemuShopFreightTemplateOptionDTO> merged = new LinkedHashMap<>();

        putOption(merged, shop.getFreightTemplateId(), null);

        List<TemuShopFreightTemplateOptionDTO> remoteTemplates = queryRemoteTemplatesQuietly(shopRecordId);
        for (TemuShopFreightTemplateOptionDTO option : remoteTemplates) {
            putOption(merged, option.freightTemplateId(), option.templateName());
        }

        List<TemuFreightTemplate> localTemplates = freightTemplateRepository.findByShopId(shop.getShopId());
        localTemplates.sort(Comparator
                .comparing((TemuFreightTemplate item) -> normalizeText(item.getTemplateName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> normalizeText(item.getFreightTemplateId()), String.CASE_INSENSITIVE_ORDER));
        for (TemuFreightTemplate template : localTemplates) {
            putOption(merged, template.getFreightTemplateId(), template.getTemplateName());
        }

        return new ArrayList<>(merged.values());
    }

    private List<TemuShopFreightTemplateOptionDTO> queryRemoteTemplatesQuietly(Long shopRecordId) {
        try {
            return queryRemoteTemplates(shopRecordId);
        } catch (Exception error) {
            log.warn("Failed to query freight templates from TEMU for shop record {}: {}", shopRecordId, error.getMessage());
            return List.of();
        }
    }

    private List<TemuShopFreightTemplateOptionDTO> queryRemoteTemplates(Long shopRecordId) throws Exception {
        TemuOpenApiCredentials creds = credentialService.getTemuOpenApiCredentialsByShopRecordIdOrThrow(shopRecordId);
        TemuOpenApiClient client = new TemuOpenApiClient(creds);

        Exception firstError = null;
        try {
            return extractTemplateOptions(client.callApiParsed(
                    TemuOpenApiClient.API_LOGISTICS_TEMPLATE,
                    null,
                    TemuOpenApiEndpoints.API_BASE_URL
            ));
        } catch (Exception error) {
            firstError = error;
            log.debug("Freight template query via partner router failed for shop record {}: {}", shopRecordId, error.getMessage());
        }

        try {
            return extractTemplateOptions(client.callApiParsed(
                    TemuOpenApiClient.API_LOGISTICS_TEMPLATE,
                    null,
                    TemuOpenApiEndpoints.API_BASE_URL_PA
            ));
        } catch (Exception error) {
            if (firstError != null) {
                error.addSuppressed(firstError);
            }
            throw error;
        }
    }

    private List<TemuShopFreightTemplateOptionDTO> extractTemplateOptions(TemuOpenApiClient.ApiResult result) {
        if (result == null) {
            return List.of();
        }
        if (!result.success) {
            throw new IllegalStateException("TEMU 查询运费模板失败: " + firstNonBlank(result.errorMsg, "未知错误"));
        }

        List<Map<String, Object>> templateRows = null;
        if (result.resultAsList() != null) {
            templateRows = result.resultAsList();
        } else {
            Map<String, Object> resultMap = result.resultAsMap();
            if (resultMap != null) {
                templateRows = extractTemplateRows(resultMap);
            }
        }

        if (templateRows == null || templateRows.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, TemuShopFreightTemplateOptionDTO> options = new LinkedHashMap<>();
        for (Map<String, Object> row : templateRows) {
            if (row == null) {
                continue;
            }
            String templateId = firstNonBlank(
                    asText(row.get("freightTemplateId")),
                    asText(row.get("templateId"))
            );
            if (!StringUtils.hasText(templateId)) {
                continue;
            }
            putOption(options, templateId, firstNonBlank(
                    asText(row.get("templateName")),
                    asText(row.get("name"))
            ));
        }
        return new ArrayList<>(options.values());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractTemplateRows(Map<String, Object> resultMap) {
        for (String key : List.of("freightTemplates", "freightTemplateList", "templateList")) {
            Object value = resultMap.get(key);
            if (value instanceof List<?> list) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> row) {
                        rows.add((Map<String, Object>) row);
                    }
                }
                return rows;
            }
        }
        return List.of();
    }

    private static void putOption(Map<String, TemuShopFreightTemplateOptionDTO> container,
                                  String freightTemplateId,
                                  String templateName) {
        String normalizedId = normalizeText(freightTemplateId);
        if (!StringUtils.hasText(normalizedId)) {
            return;
        }

        TemuShopFreightTemplateOptionDTO existing = container.get(normalizedId);
        String mergedName = firstNonBlank(
                templateName,
                existing == null ? null : existing.templateName()
        );
        container.put(normalizedId, new TemuShopFreightTemplateOptionDTO(normalizedId, mergedName));
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : normalizeText(second);
    }
}
