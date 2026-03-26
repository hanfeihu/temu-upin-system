package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.TemuCategoryDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tminos.temu.upin.sdk.v2.category.CategoryApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemuCategoryService {

    private static final Logger log = LoggerFactory.getLogger(TemuCategoryService.class);

    private final ObjectMapper objectMapper;
    private final TemuOpenApiCredentialService temuOpenApiCredentialService;

    public TemuCategoryDTO.MatchCategoryResponse matchCategory(String title) {
        if (title == null || title.isBlank()) {
            return TemuCategoryDTO.MatchCategoryResponse.builder()
                    .success(false)
                    .errorMsg("title is required")
                    .build();
        }

        try {
            TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            CategoryApiClient client = new CategoryApiClient(creds);
            String raw = client.matchCategory(title.trim());
            JsonNode root = objectMapper.readTree(raw);

            TemuCategoryDTO.MatchCategoryResponse.MatchCategoryResponseBuilder b = TemuCategoryDTO.MatchCategoryResponse.builder();
            b.requestId(text(root, "requestId"));
            b.success(root.path("success").asBoolean(false));
            if (root.has("errorCode") && !root.get("errorCode").isNull()) {
                b.errorCode(root.get("errorCode").asInt());
            }
            b.errorMsg(text(root, "errorMsg"));

            List<TemuCategoryDTO.CategoryPath> paths = new ArrayList<>();
            List<TemuCategoryDTO.MatchOption> options = new ArrayList<>();
            JsonNode dtos = root.path("result").path("categoryPathDTOS");
            if (dtos.isArray()) {
                for (JsonNode dto : dtos) {
                    TemuCategoryDTO.CategoryPath p = parsePath(dto);
                    paths.add(p);
                    options.add(TemuCategoryDTO.MatchOption.builder()
                            .pathIds(p.pathIdsCsv())
                            .pathNames(p.pathNamesArrow())
                            .leafId(p.leafId())
                            .leafName(p.leafName())
                            .pathText(p.pathText())
                            .build());
                }
            }
            b.categoryPaths(paths);
            b.options(options);
            return b.build();
        } catch (Exception e) {
            return TemuCategoryDTO.MatchCategoryResponse.builder()
                    .success(false)
                    .errorMsg(e.getMessage())
                    .build();
        }
    }

    public String getCategoryAttributesRaw(String leafCatId) {
        return fetchCategoryAttributesRaw(leafCatId).raw();
    }

    public CategoryAttributesFetchResult fetchCategoryAttributesRaw(String leafCatId) {
        if (leafCatId == null || leafCatId.isBlank()) {
            return new CategoryAttributesFetchResult(null, "leafCatId is blank");
        }
        try {
            TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            CategoryApiClient client = new CategoryApiClient(creds);
            String raw = client.getCategoryAttributes(Integer.valueOf(leafCatId.trim()));
            return new CategoryAttributesFetchResult(raw, null);
        } catch (Exception e) {
            String error = StringUtils.hasText(e.getMessage()) ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getSimpleName();
            log.warn("fetchCategoryAttributesRaw failed leafCatId={} error={}", leafCatId, error);
            return new CategoryAttributesFetchResult(null, error);
        }
    }

    public record CategoryAttributesFetchResult(String raw, String errorMsg) {
    }

    public List<TemuCategoryDTO.ParentSpecOption> listParentSpecs() {
        try {
            TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            CategoryApiClient client = new CategoryApiClient(creds);
            String raw = client.getParentSpecList();
            JsonNode root = objectMapper.readTree(raw);
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalStateException(text(root, "errorMsg"));
            }
            Map<String, TemuCategoryDTO.ParentSpecOption> byName = new LinkedHashMap<>();
            collectParentSpecsInto(root.path("result"), byName);
            return new ArrayList<>(byName.values());
        } catch (Exception e) {
            throw new IllegalStateException(StringUtils.hasText(e.getMessage()) ? e.getMessage() : "获取 TEMU 父规格失败", e);
        }
    }

    private void collectParentSpecsInto(JsonNode node, Map<String, TemuCategoryDTO.ParentSpecOption> byName) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode idNode = node.get("parentSpecId");
            JsonNode nameNode = node.get("parentSpecName");
            if (idNode != null && !idNode.isNull() && nameNode != null && !nameNode.isNull()) {
                int id = idNode.asInt(0);
                String name = nameNode.asText("");
                if (id > 0 && StringUtils.hasText(name) && !byName.containsKey(name.trim())) {
                    byName.put(name.trim(), TemuCategoryDTO.ParentSpecOption.builder()
                            .parentSpecId(id)
                            .parentSpecName(name.trim())
                            .build());
                }
            }
            node.fields().forEachRemaining(entry -> collectParentSpecsInto(entry.getValue(), byName));
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectParentSpecsInto(item, byName);
            }
        }
    }

    private TemuCategoryDTO.CategoryPath parsePath(JsonNode dto) {
        return TemuCategoryDTO.CategoryPath.builder()
                .cat1(parseNode(dto.get("cat1DTO")))
                .cat2(parseNode(dto.get("cat2DTO")))
                .cat3(parseNode(dto.get("cat3DTO")))
                .cat4(parseNode(dto.get("cat4DTO")))
                .cat5(parseNode(dto.get("cat5DTO")))
                .cat6(parseNode(dto.get("cat6DTO")))
                .cat7(parseNode(dto.get("cat7DTO")))
                .cat8(parseNode(dto.get("cat8DTO")))
                .cat9(parseNode(dto.get("cat9DTO")))
                .cat10(parseNode(dto.get("cat10DTO")))
                .build();
    }

    private TemuCategoryDTO.CategoryNode parseNode(JsonNode n) {
        if (n == null || n.isNull()) return null;
        TemuCategoryDTO.CategoryNode.CategoryNodeBuilder b = TemuCategoryDTO.CategoryNode.builder();
        if (n.has("catId") && !n.get("catId").isNull()) b.catId(n.get("catId").asLong());
        b.catName(text(n, "catName"));
        if (n.has("parentCatId") && !n.get("parentCatId").isNull()) b.parentCatId(n.get("parentCatId").asLong());
        if (n.has("catType") && !n.get("catType").isNull()) b.catType(n.get("catType").asInt());
        if (n.has("isLeaf") && !n.get("isLeaf").isNull()) b.isLeaf(n.get("isLeaf").asBoolean());
        if (n.has("hiddenType") && !n.get("hiddenType").isNull()) b.hiddenType(n.get("hiddenType").asInt());
        if (n.has("catLevel") && !n.get("catLevel").isNull()) b.catLevel(n.get("catLevel").asInt());
        if (n.has("isHidden") && !n.get("isHidden").isNull()) b.isHidden(n.get("isHidden").asBoolean());
        return b.build();
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
