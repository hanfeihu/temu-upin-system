package com.tminos.productscene.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DimensionExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ignoresShopDomainFragmentsThatLookLikeMeterUnits() {
        String parsedJson = """
                {
                  "packagingLength": null,
                  "packagingWidth": null,
                  "packagingHeight": null,
                  "attributesDataJson": "{\\"规格\\":\\"浇花神器 蓝色,浇花神器 绿色\\"}",
                  "rawHtml": "action=\\"https://shop0250l62491ma4.1688.com/page/offerlist.html\\""
                }
                """;

        DimensionExtractor.DimensionResult result = DimensionExtractor.fromDetailJson(parsedJson, null, objectMapper);

        assertNull(result);
    }

    @Test
    void stillReadsRealProductAttributeDimensions() {
        String parsedJson = """
                {
                  "attributesDataJson": "{\\"产品尺寸\\":\\"12x8x3cm\\"}"
                }
                """;

        DimensionExtractor.DimensionResult result = DimensionExtractor.fromDetailJson(parsedJson, null, objectMapper);

        assertEquals(new BigDecimal("12.00"), result.maxDimensionCm());
        assertEquals("商品属性", result.source());
    }

    @Test
    void ignoresImpossibleAttributeDimensions() {
        String parsedJson = """
                {
                  "attributesDataJson": "{\\"规格\\":\\"864米\\"}"
                }
                """;

        DimensionExtractor.DimensionResult result = DimensionExtractor.fromDetailJson(parsedJson, null, objectMapper);

        assertNull(result);
    }

    @Test
    void ignoresNonShippingMarketingLengths() {
        DimensionExtractor.DimensionResult result = DimensionExtractor.fromSku("黑色笔芯/800米书写长度>ST_0.5mm", null, objectMapper);

        assertEquals(new BigDecimal("0.05"), result.maxDimensionCm());
    }

    @Test
    void ignoresPreviouslyStoredImpossibleDimensionsWhenTakingMax() {
        DimensionExtractor.DimensionResult result = DimensionExtractor.max(
                new DimensionExtractor.DimensionResult(new BigDecimal("86400.00"), "SKU", "old dirty value"),
                new DimensionExtractor.DimensionResult(new BigDecimal("20.00"), "商品属性", "20cm")
        );

        assertEquals(new BigDecimal("20.00"), result.maxDimensionCm());
    }
}
