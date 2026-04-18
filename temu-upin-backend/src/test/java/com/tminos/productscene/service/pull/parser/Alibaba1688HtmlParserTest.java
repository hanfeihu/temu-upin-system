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
    void shouldDeduplicateAlibabaImageVariantsToOriginalImage() throws Exception {
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
        assertEquals(List.of("https://cbu01.alicdn.com/img/ibank/O1CN01good_!!1-0-cib.jpg"), detailImages);
    }
}