package com.tminos.temu.upin.sdk.v2.dto;

import java.util.List;

/** DTO: bg.local.goods.add 响应体 result */
public class AddGloGoodsResponse {
    private Long goodsId;
    private Integer productType;
    private List<SkuInfo> skuInfoList;

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public Integer getProductType() {
        return productType;
    }

    public void setProductType(Integer productType) {
        this.productType = productType;
    }

    public List<SkuInfo> getSkuInfoList() {
        return skuInfoList;
    }

    public void setSkuInfoList(List<SkuInfo> skuInfoList) {
        this.skuInfoList = skuInfoList;
    }

    public static class SkuInfo {
        private Long skuId;
        private String outSkuSn;
        private List<SpecInfo> specList;

        public Long getSkuId() {
            return skuId;
        }

        public void setSkuId(Long skuId) {
            this.skuId = skuId;
        }

        public String getOutSkuSn() {
            return outSkuSn;
        }

        public void setOutSkuSn(String outSkuSn) {
            this.outSkuSn = outSkuSn;
        }

        public List<SpecInfo> getSpecList() {
            return specList;
        }

        public void setSpecList(List<SpecInfo> specList) {
            this.specList = specList;
        }
    }

    public static class SpecInfo {
        private Long specId;
        private Long parentSpecId;

        public Long getSpecId() {
            return specId;
        }

        public void setSpecId(Long specId) {
            this.specId = specId;
        }

        public Long getParentSpecId() {
            return parentSpecId;
        }

        public void setParentSpecId(Long parentSpecId) {
            this.parentSpecId = parentSpecId;
        }
    }
}
