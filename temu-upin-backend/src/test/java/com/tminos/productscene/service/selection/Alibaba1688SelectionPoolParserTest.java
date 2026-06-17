package com.tminos.productscene.service.selection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Alibaba1688SelectionPoolParserTest {

    @Test
    void shouldParseCardUnitStartBatchQtyFromMoqText() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1401L)
                .offerId("1045224419109")
                .detailUrl("https://detail.1688.com/offer/1045224419109.html")
                .sourcePlatform("1688")
                .productName("测试卡片商品")
                .parsedJson("""
                        {
                          "productName":"测试卡片商品",
                          "moq":360,
                          "moqText":"¥2.00 1卡起批 ¥1.90 360-1799卡 ¥1.80 ≥1800卡"
                        }
                        """)
                .extractedJson("{}")
                .createdAt(LocalDateTime.of(2026, 5, 11, 17, 10, 0))
                .lastCollectedAt(LocalDateTime.of(2026, 5, 11, 17, 10, 0))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals(1, parsed.main().getMoqSnapshot());
        assertEquals(1, parsed.main().getStartBatchQtySnapshot());
    }

    @Test
    void shouldParseStartOrderTextAsStartBatchQty() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1402L)
                .offerId("1043531045432")
                .detailUrl("https://detail.1688.com/offer/1043531045432.html")
                .sourcePlatform("1688")
                .productName("测试起定商品")
                .parsedJson("""
                        {
                          "productName":"测试起定商品",
                          "moq":5000,
                          "moqText":"¥0.65 1支起定 ¥0.62 5000-9999支 ¥0.60 ≥10000支 已售100+支"
                        }
                        """)
                .extractedJson("{}")
                .createdAt(LocalDateTime.of(2026, 5, 11, 17, 15, 0))
                .lastCollectedAt(LocalDateTime.of(2026, 5, 11, 17, 15, 0))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals(1, parsed.main().getMoqSnapshot());
        assertEquals(1, parsed.main().getStartBatchQtySnapshot());
    }

    @Test
    void shouldParseCommonExtendedStartBatchUnits() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1403L)
                .offerId("1038205044073")
                .detailUrl("https://detail.1688.com/offer/1038205044073.html")
                .sourcePlatform("1688")
                .productName("测试扩展单位商品")
                .parsedJson("""
                        {
                          "productName":"测试扩展单位商品",
                          "moq":30,
                          "moqText":"新人价 ¥3.24 起 首件预估到手价 ¥6.24 ¥30.00 1桶起批 已售30+桶"
                        }
                        """)
                .extractedJson("{}")
                .createdAt(LocalDateTime.of(2026, 5, 11, 17, 30, 0))
                .lastCollectedAt(LocalDateTime.of(2026, 5, 11, 17, 30, 0))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals(1, parsed.main().getMoqSnapshot());
        assertEquals(1, parsed.main().getStartBatchQtySnapshot());
    }

    @Test
    void shouldFallbackToRawHtmlMetaWhenParsedPayloadIsMostlyEmpty() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1399L)
                .offerId("1027836883141")
                .detailUrl("https://detail.1688.com/offer/1027836883141.html")
                .sourcePlatform("1688")
                .productName("金苔鼠鱼清道夫吃鱼缸青苔垃圾除藻清洁工具鱼热带小型淡水鱼-阿里巴巴")
                .parsedJson("{}")
                .extractedJson("""
                        {
                          "purchaseAssistant": {
                            "monthlySoldText": "月成交 123笔",
                            "categoryText": "观赏鱼"
                          }
                        }
                        """)
                .rawHtml("""
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <title>金苔鼠鱼清道夫吃鱼缸青苔垃圾除藻清洁工具鱼热带小型淡水鱼-阿里巴巴</title>
                          <meta property="og:title" content="金苔鼠鱼清道夫吃鱼缸青苔垃圾除藻清洁工具鱼热带小型淡水鱼" />
                          <meta property="og:image" content="https://cbu01.alicdn.com/img/ibank/O1CN01xCZMiG1u8OJ83Z6ZO_!!2220434335992-0-cib.310x310.jpg" />
                          <meta property="og:product:nick" content="name=南通君骏一商贸有限公司; url=//shop55720462584q1.1688.com" />
                          <meta name="location" content="province=广东省;city=广州市" />
                          <meta property="og:product:base_score" content="5" />
                        </head>
                        <body></body>
                        </html>
                        """)
                .createdAt(LocalDateTime.of(2026, 4, 23, 1, 26, 3))
                .lastCollectedAt(LocalDateTime.of(2026, 4, 23, 1, 26, 3))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals("金苔鼠鱼清道夫吃鱼缸青苔垃圾除藻清洁工具鱼热带小型淡水鱼-阿里巴巴", parsed.main().getProductTitleSnapshot());
        assertEquals("https://cbu01.alicdn.com/img/ibank/O1CN01xCZMiG1u8OJ83Z6ZO_!!2220434335992-0-cib.310x310.jpg", parsed.main().getMainImageSnapshot());
        assertEquals(List.of("https://cbu01.alicdn.com/img/ibank/O1CN01xCZMiG1u8OJ83Z6ZO_!!2220434335992-0-cib.310x310.jpg"), parsed.main().getCarouselImageUrls());
        assertEquals("南通君骏一商贸有限公司", parsed.main().getCompanyNameSnapshot());
        assertEquals(new BigDecimal("5"), parsed.main().getServiceScoreSnapshot());
        assertEquals("广东省 广州市", parsed.main().getShippingLocationSnapshot());
        assertEquals("观赏鱼", parsed.main().getCategorySnapshot());
        assertEquals("月成交 123笔", parsed.main().getMonthlySalesSnapshot());
    }

    @Test
    void shouldKeepSingleAnonymousSkuWhenPriceExists() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1313L)
                .offerId("1028751056854")
                .detailUrl("https://detail.1688.com/offer/1028751056854.html")
                .sourcePlatform("1688")
                .productName("硫醚沙星钓鱼专用高浓度鲤鲫鱼草鱼饵料添加剂强效诱鱼剂厂家直销")
                .parsedJson("{\"skuDataJson\":\"{\\\"skus\\\":[{\\\"name\\\":\\\"\\\",\\\"price\\\":2.68,\\\"stock\\\":9993877}]}\"}")
                .createdAt(LocalDateTime.of(2026, 4, 23, 0, 34, 23))
                .lastCollectedAt(LocalDateTime.of(2026, 4, 23, 0, 34, 23))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals(1, parsed.skus().size());
        assertEquals("默认规格", parsed.skus().get(0).getSkuSpecText());
        assertEquals(new BigDecimal("2.68"), parsed.skus().get(0).getSkuPriceSnapshot());
        assertEquals(Integer.valueOf(9993877), parsed.skus().get(0).getPageStockSnapshot());
        assertNotNull(parsed.skus().get(0).getSkuSpecJson());
    }

    @Test
    void shouldPreferMoreCompleteExtractedDetailImagesWhenParsedOnlyHasSubset() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1399L)
                .offerId("1027836883141")
                .detailUrl("https://detail.1688.com/offer/1027836883141.html")
                .sourcePlatform("1688")
                .productName("测试商品")
                .parsedJson("""
                        {
                          "detailImagesJson":"[\\"https://cbu01.alicdn.com/img/ibank/O1CN01main_!!1-0-cib.jpg\\"]"
                        }
                        """)
                .extractedJson("""
                        {
                          "detailImages": [
                            "https://cbu01.alicdn.com/img/ibank/O1CN01extraA_!!1-0-cib.jpg?x-oss-process=image/format,png",
                            "https://cbu01.alicdn.com/img/ibank/O1CN01extraB_!!1-0-cib.jpg?x-oss-process=image/format,png",
                            "https://cbu01.alicdn.com/img/ibank/O1CN01main_!!1-0-cib.jpg"
                          ]
                        }
                        """)
                .createdAt(LocalDateTime.of(2026, 4, 23, 1, 26, 3))
                .lastCollectedAt(LocalDateTime.of(2026, 4, 23, 9, 28, 34))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01extraA_!!1-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01extraB_!!1-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01main_!!1-0-cib.jpg"
        ), parsed.main().getDetailImageUrls());
    }

    @Test
    void shouldPreferFreshRawHtmlDetailImagesOverStaleParsedPayload() {
        Alibaba1688SelectionPoolParser parser = new Alibaba1688SelectionPoolParser(new ObjectMapper());
        Alibaba1688DetailRecord detailRecord = Alibaba1688DetailRecord.builder()
                .id(1259L)
                .offerId("833623803389")
                .detailUrl("https://detail.1688.com/offer/833623803389.html")
                .sourcePlatform("1688")
                .productName("测试商品")
                .parsedJson("""
                        {
                          "productName":"测试商品",
                          "productMainImage":"https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg_b.jpg",
                          "carouselImagesJson":"[\\"https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg_b.jpg\\"]",
                          "detailImagesJson":"[\\"https://cbu01.alicdn.com/img/ibank/O1CN01RBCOWe1a87oFlCGu3_!!2214423013284-0-cib.jpg\\",\\"https://cbu01.alicdn.com/imgextra/i2/O1CN01HI4gGf1PDengJ02RP_!!4611686018427381519-0-cbu_common_content.jpg\\"]"
                        }
                        """)
                .extractedJson("""
                        {
                          "detailImages": [
                            "https://cbu01.alicdn.com/img/ibank/O1CN01RBCOWe1a87oFlCGu3_!!2214423013284-0-cib.jpg",
                            "https://cbu01.alicdn.com/imgextra/i2/O1CN01HI4gGf1PDengJ02RP_!!4611686018427381519-0-cbu_common_content.jpg"
                          ]
                        }
                        """)
                .rawHtml("""
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <link rel="canonical" href="https://detail.1688.com/offer/833623803389.html">
                          <title>测试商品</title>
                        </head>
                        <body>
                          <div id="productTitle"><h1>测试商品</h1></div>
                          <div id="gallery">
                            <div class="od-gallery-preview"><img src="https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg"></div>
                          </div>
                          <v-detail-i class="html-description">
                            <template shadowrootmode="open">
                              <div id="detail">
                                <div style="width: 790px;">
                                  <div style="display: block;" class="sdmap-dynamic-offer-list">
                                    <div class="desc-dynamic-module offer-list-wapper">
                                      <ul>
                                        <li><a><div class="offer-image-wrap"><img src="https://cbu01.alicdn.com/img/ibank/O1CN01RBCOWe1a87oFlCGu3_!!2214423013284-0-cib.jpg"></div></a></li>
                                        <li><a><div class="offer-image-wrap"><img src="https://cbu01.alicdn.com/imgextra/i2/O1CN01HI4gGf1PDengJ02RP_!!4611686018427381519-0-cbu_common_content.jpg"></div></a></li>
                                      </ul>
                                    </div>
                                  </div>
                                  <img class="dynamic-backup-img" src="https://cbu01.alicdn.com/img/ibank/O1CN01xXl1Xw1Bs2zPUMXLh_!!0-0-cib.jpg" style="display:none;">
                                  <img src="https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg">
                                  <img src="https://cbu01.alicdn.com/img/ibank/O1CN01UDD8e41a87fb6dnKm_!!2214423013284-0-cib.jpg">
                                  <img src="https://cbu01.alicdn.com/img/ibank/O1CN01hc7UmY1a87fSaufzt_!!2214423013284-0-cib.jpg">
                                  <img src="https://cbu01.alicdn.com/img/ibank/O1CN01Qe6Feq1a87fagUMQn_!!2214423013284-0-cib.jpg">
                                  <img src="https://cbu01.alicdn.com/img/ibank/O1CN01rPi7z31a87fX2jD9H_!!2214423013284-0-cib.jpg">
                                  <img src="https://cbu01.alicdn.com/img/ibank/O1CN01lAjq2D1a87fZy5E7r_!!2214423013284-0-cib.jpg">
                                </div>
                              </div>
                            </template>
                          </v-detail-i>
                        </body>
                        </html>
                        """)
                .createdAt(LocalDateTime.of(2026, 4, 23, 1, 26, 3))
                .lastCollectedAt(LocalDateTime.of(2026, 4, 23, 9, 28, 34))
                .build();

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);

        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01xXl1Xw1Bs2zPUMXLh_!!0-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01UDD8e41a87fb6dnKm_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01hc7UmY1a87fSaufzt_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01Qe6Feq1a87fagUMQn_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01rPi7z31a87fX2jD9H_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01lAjq2D1a87fZy5E7r_!!2214423013284-0-cib.jpg"
        ), parsed.main().getDetailImageUrls());
    }
}
