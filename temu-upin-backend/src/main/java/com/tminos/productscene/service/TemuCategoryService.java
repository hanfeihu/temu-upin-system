package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.TemuCategoryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.tminos.temu.upin.sdk.v2.category.CategoryApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TemuCategoryService {

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
        if (leafCatId == null || leafCatId.isBlank()) {
            return null;
        }
        try {
            TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            CategoryApiClient client = new CategoryApiClient(creds);
            return client.getCategoryAttributes(Integer.valueOf(leafCatId.trim()));
        } catch (Exception e) {
            return null;
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
