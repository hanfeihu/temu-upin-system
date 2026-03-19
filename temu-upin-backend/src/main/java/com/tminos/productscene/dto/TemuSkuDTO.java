package com.tminos.productscene.dto;

import java.math.BigDecimal;
import java.util.List;

public class TemuSkuDTO {

    public static class TemuSkuRow {
        private Long id;
        private String temuSkuId;
        private String originSkuId;
        private String specKey;
        private String specJson;
        private String image;
        private BigDecimal originPrice;
        private BigDecimal supplyPrice;
        private Integer weightG;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;

        public TemuSkuRow() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTemuSkuId() { return temuSkuId; }
        public void setTemuSkuId(String temuSkuId) { this.temuSkuId = temuSkuId; }
        public String getOriginSkuId() { return originSkuId; }
        public void setOriginSkuId(String originSkuId) { this.originSkuId = originSkuId; }
        public String getSpecKey() { return specKey; }
        public void setSpecKey(String specKey) { this.specKey = specKey; }
        public String getSpecJson() { return specJson; }
        public void setSpecJson(String specJson) { this.specJson = specJson; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        public BigDecimal getOriginPrice() { return originPrice; }
        public void setOriginPrice(BigDecimal originPrice) { this.originPrice = originPrice; }
        public BigDecimal getSupplyPrice() { return supplyPrice; }
        public void setSupplyPrice(BigDecimal supplyPrice) { this.supplyPrice = supplyPrice; }
        public Integer getWeightG() { return weightG; }
        public void setWeightG(Integer weightG) { this.weightG = weightG; }
        public BigDecimal getLengthCm() { return lengthCm; }
        public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
        public BigDecimal getWidthCm() { return widthCm; }
        public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
        public BigDecimal getHeightCm() { return heightCm; }
        public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    }

    public static class InitTemuSkusRequest {
        private Boolean force;

        public InitTemuSkusRequest() {}
        public Boolean getForce() { return force; }
        public void setForce(Boolean force) { this.force = force; }
    }

    public static class SaveTemuSkusRequest {
        private List<TemuSkuRow> skus;

        public SaveTemuSkusRequest() {}
        public List<TemuSkuRow> getSkus() { return skus; }
        public void setSkus(List<TemuSkuRow> skus) { this.skus = skus; }
    }
}
