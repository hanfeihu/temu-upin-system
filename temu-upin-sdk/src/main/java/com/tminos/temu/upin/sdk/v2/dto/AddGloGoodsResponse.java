package com.tminos.temu.upin.sdk.v2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: bg.local.goods.add 响应体 result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddGloGoodsResponse {
    private Long goodsId;           // 商品ID
    private Integer productType;    // 1/2/3
    private List<SkuInfo> skuInfoList;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SkuInfo {
        private Long skuId;
        private String outSkuSn;
        private List<SpecInfo> specList;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SpecInfo {
        private Long specId;
        private Long parentSpecId;
    }
}
