package com.tminos.productscene.service.pull.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Alibaba1688HtmlParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldFilterMalformedDetailImageUrls() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1234567890.html\">
                    <title>测试商品</title>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <div id=\"detail\">
                        <img src='[Pasted ~1 linescib.310x310.jpg\\",\\"https://cbu01.alicdn.com/img/ibank/O1CN01bad_!!1-0-cib.220x220.jpg\\",\\"https://cbu01.alicdn.com/img/ibank/O1CN01bad_!!1-0-cib.search.jpg\\",0x310'>
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.jpg'>
                    </div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        List<String> detailImages = objectMapper.readValue(parsed.getDetailImagesJson(), new TypeReference<>() {});
        assertEquals(List.of("https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.jpg"), detailImages);
    }

    @Test
    void shouldKeepAlibabaImageVariantsAsOriginalUrls() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1234567890.html\">
                    <title>测试商品</title>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <div id=\"detail\">
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.jpg'>
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.220x220.jpg'>
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.search.jpg'>
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.summ.jpg'>
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.310x310.jpg'>
                    </div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        List<String> detailImages = objectMapper.readValue(parsed.getDetailImagesJson(), new TypeReference<>() {});
        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.220x220.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.search.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.summ.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.310x310.jpg"
        ), detailImages);
    }

    @Test
    void shouldKeepDetailImageQueryStringAsOriginalUrl() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1234567890.html\">
                    <title>测试商品</title>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <div id=\"detail\">
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.search.jpg?x-oss-process=image/resize,w_800'>
                    </div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        List<String> detailImages = objectMapper.readValue(parsed.getDetailImagesJson(), new TypeReference<>() {});
        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.search.jpg?x-oss-process=image/resize,w_800"
        ), detailImages);
    }

    @Test
    void shouldKeepCarouselImageUrlAsOriginalValue() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1234567890.html\">
                    <title>测试商品</title>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <div id=\"gallery\">
                        <div class=\"od-scroller-list-wapper\">
                            <div class=\"od-scroller-item\">
                                <img src='https://cbu01.alicdn.com/img/ibank/O1CN01carousel_!!1-0-cib.jpg_.webp'>
                            </div>
                        </div>
                    </div>
                    <div id=\"detail\">
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01goodA_!!1-0-cib.jpg'>
                    </div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        List<String> carouselImages = objectMapper.readValue(parsed.getCarouselImagesJson(), new TypeReference<>() {});
        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01carousel_!!1-0-cib.jpg_.webp"
        ), carouselImages);
    }

    @Test
    void shouldFilterDecorativeDetailImagesFromRawDetailBlock() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1234567890.html\">
                    <title>测试商品</title>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <div id=\"detail\">
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01goodA_!!1-0-cib.jpg'>
                        <img src='https://img.alicdn.com/imgextra/i4/O1CN01icon_!!6000000000950-2-tps-24-23.png'>
                        <img src='https://gw.alicdn.com/imgextra/i3/O1CN01badge_!!6000000002371-2-tps-160-160.png'>
                        <img src='https://img.alicdn.com/imgextra/i4/6000000000038/O1CN01service_!!6000000000038-2-gg_dtc.png'>
                    </div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        List<String> detailImages = objectMapper.readValue(parsed.getDetailImagesJson(), new TypeReference<>() {});
        assertEquals(List.of("https://cbu01.alicdn.com/img/ibank/O1CN01goodA_!!1-0-cib.jpg"), detailImages);
    }

    @Test
    void shouldPreferItemCdnDetailImagesWhenRawBlockContainsDecorativeAssets() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1234567890.html\">
                    <title>测试商品</title>
                    <script>
                        window.context=(function(b,d){return d})(window.contextPath,{
                          "result":{
                            "data":{
                              "description":{
                                "fields":{
                                  "detailUrl":"https://itemcdn.tmall.com/1688offer/mock-detail"
                                }
                              }
                            }
                          }
                        });
                    </script>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <div id=\"detail\">
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01rawA_!!1-0-cib.jpg'>
                        <img src='https://img.alicdn.com/imgextra/i4/O1CN01icon_!!6000000000950-2-tps-24-23.png'>
                        <img src='https://cbu01.alicdn.com/img/ibank/O1CN01rawB_!!2-0-cib.jpg'>
                    </div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper) {
            @Override
            protected List<String> extractDetailImagesFromItemCdn(String detailUrl) {
                assertEquals("https://itemcdn.tmall.com/1688offer/mock-detail", detailUrl);
                return List.of(
                        "https://cbu01.alicdn.com/img/ibank/O1CN01cleanA_!!3-0-cib.jpg",
                        "https://cbu01.alicdn.com/img/ibank/O1CN01cleanB_!!3-0-cib.jpg"
                );
            }
        };

        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);
        List<String> detailImages = objectMapper.readValue(parsed.getDetailImagesJson(), new TypeReference<>() {});

        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01cleanA_!!3-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01cleanB_!!3-0-cib.jpg"
        ), detailImages);
    }

    @Test
    void shouldExcludeShopRecommendationImagesFromDetailBlock() throws Exception {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/833623803389.html\">
                    <title>测试商品</title>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                    <v-detail-i class=\"html-description\">
                      <template shadowrootmode=\"open\">
                        <div id=\"detail\">
                          <div style=\"width: 790px;\">
                            <div style=\"display: block;\" class=\"sdmap-dynamic-offer-list\">
                              <div class=\"desc-dynamic-module offer-list-wapper\">
                                <ul>
                                  <li><a><div class=\"offer-image-wrap\"><img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01RBCOWe1a87oFlCGu3_!!2214423013284-0-cib.jpg\"></div></a></li>
                                  <li><a><div class=\"offer-image-wrap\"><img src=\"https://cbu01.alicdn.com/imgextra/i2/O1CN01HI4gGf1PDengJ02RP_!!4611686018427381519-0-cbu_common_content.jpg\"></div></a></li>
                                </ul>
                              </div>
                            </div>
                            <img class=\"dynamic-backup-img\" src=\"https://cbu01.alicdn.com/img/ibank/O1CN01xXl1Xw1Bs2zPUMXLh_!!0-0-cib.jpg\" style=\"display:none;\">
                            <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg\">
                            <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01UDD8e41a87fb6dnKm_!!2214423013284-0-cib.jpg\">
                            <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01hc7UmY1a87fSaufzt_!!2214423013284-0-cib.jpg\">
                            <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01Qe6Feq1a87fagUMQn_!!2214423013284-0-cib.jpg\">
                            <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01rPi7z31a87fX2jD9H_!!2214423013284-0-cib.jpg\">
                            <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01lAjq2D1a87fZy5E7r_!!2214423013284-0-cib.jpg\">
                          </div>
                        </div>
                        <div id=\"detail-notice-container-bottom\"></div>
                        <div class=\"od-gallery-preview\">
                          <img src=\"https://cbu01.alicdn.com/img/ibank/O1CN01qNvHKO1Bs2s4zV4Re_!!0-0-cib.jpg\">
                        </div>
                      </template>
                    </v-detail-i>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        List<String> detailImages = objectMapper.readValue(parsed.getDetailImagesJson(), new TypeReference<>() {});
        assertEquals(List.of(
                "https://cbu01.alicdn.com/img/ibank/O1CN01xXl1Xw1Bs2zPUMXLh_!!0-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01rvEsBY1a87fZS7ciC_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01UDD8e41a87fb6dnKm_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01hc7UmY1a87fSaufzt_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01Qe6Feq1a87fagUMQn_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01rPi7z31a87fX2jD9H_!!2214423013284-0-cib.jpg",
                "https://cbu01.alicdn.com/img/ibank/O1CN01lAjq2D1a87fZy5E7r_!!2214423013284-0-cib.jpg"
        ), detailImages);
    }

    @Test
    void shouldPreferCategoryFromWindowContext() {
        String html = """
                <html>
                <head>
                    <link rel=\"canonical\" href=\"https://detail.1688.com/offer/1028271995606.html\">
                    <title>测试商品</title>
                    <script>
                        window.context=(function(b,d){return d})(window.contextPath,{
                          "result":{
                            "data":{
                              "description":{
                                "fields":{
                                  "leafCategoryId":1047065
                                }
                              },
                              "offerSubject":{
                                "fields":{
                                  "leafCategoryName":"洗发水"
                                }
                              },
                              "tradeModel":{
                                "tempModel":{
                                  "postCategoryId":1047065,
                                  "secondCategoryId":10313,
                                  "topCategoryId":130822220
                                }
                              }
                            }
                          }
                        });
                    </script>
                </head>
                <body>
                    <div id=\"productTitle\"><h1>测试商品</h1></div>
                </body>
                </html>
                """;

        Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
        Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(html);

        assertEquals("洗发水", parsed.getProductCategory());
        assertEquals("洗发水", parsed.getOriginalCategory());
        assertEquals("洗发水", parsed.getLeafCategoryName());
        assertEquals("1047065", parsed.getLeafCategoryId());
        assertEquals("1047065", parsed.getPostCategoryId());
        assertEquals("10313", parsed.getSecondCategoryId());
        assertEquals("130822220", parsed.getTopCategoryId());
    }
}
