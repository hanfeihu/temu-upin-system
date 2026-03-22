package com.tminos.temu.upin.sdk.v2.goods;

import java.util.List;
import java.util.Map;

public class AddGloGoodsRequest {
    private Integer cat1Id;
    private Integer cat2Id;
    private Integer cat3Id;
    private Integer cat4Id;
    private Integer cat5Id;
    private Integer cat6Id;
    private Integer cat7Id;
    private Integer cat8Id;
    private Integer cat9Id;
    private Integer cat10Id;

    private String productName;
    private List<ProductI18nReq> productI18nReqs;
    private Boolean isRecommendedTag;

    private String materialImgUrl;
    private List<String> carouselImageUrls;

    private ProductSemiManagedReq productSemiManagedReq;
    private ProductWarehouseRouteReq productWarehouseRouteReq;
    private ProductWhExtAttrReq productWhExtAttrReq;
    private ProductShipmentReq productShipmentReq;

    private List<ProductPropertyReq> productPropertyReqs;
    private List<ProductSkuReq> productSkuReqs;
    private ProductSkcReq productSkcReq;
    private List<ProductSpecPropertyReq> productSpecPropertyReqs;
    private Integer sizeTemplateId;

    private Map<String, Object> extra;

    public Integer getCat1Id() { return cat1Id; }
    public void setCat1Id(Integer cat1Id) { this.cat1Id = cat1Id; }
    public Integer getCat2Id() { return cat2Id; }
    public void setCat2Id(Integer cat2Id) { this.cat2Id = cat2Id; }
    public Integer getCat3Id() { return cat3Id; }
    public void setCat3Id(Integer cat3Id) { this.cat3Id = cat3Id; }
    public Integer getCat4Id() { return cat4Id; }
    public void setCat4Id(Integer cat4Id) { this.cat4Id = cat4Id; }
    public Integer getCat5Id() { return cat5Id; }
    public void setCat5Id(Integer cat5Id) { this.cat5Id = cat5Id; }
    public Integer getCat6Id() { return cat6Id; }
    public void setCat6Id(Integer cat6Id) { this.cat6Id = cat6Id; }
    public Integer getCat7Id() { return cat7Id; }
    public void setCat7Id(Integer cat7Id) { this.cat7Id = cat7Id; }
    public Integer getCat8Id() { return cat8Id; }
    public void setCat8Id(Integer cat8Id) { this.cat8Id = cat8Id; }
    public Integer getCat9Id() { return cat9Id; }
    public void setCat9Id(Integer cat9Id) { this.cat9Id = cat9Id; }
    public Integer getCat10Id() { return cat10Id; }
    public void setCat10Id(Integer cat10Id) { this.cat10Id = cat10Id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public List<ProductI18nReq> getProductI18nReqs() { return productI18nReqs; }
    public void setProductI18nReqs(List<ProductI18nReq> productI18nReqs) { this.productI18nReqs = productI18nReqs; }
    public Boolean getIsRecommendedTag() { return isRecommendedTag; }
    public void setIsRecommendedTag(Boolean isRecommendedTag) { this.isRecommendedTag = isRecommendedTag; }
    public String getMaterialImgUrl() { return materialImgUrl; }
    public void setMaterialImgUrl(String materialImgUrl) { this.materialImgUrl = materialImgUrl; }
    public List<String> getCarouselImageUrls() { return carouselImageUrls; }
    public void setCarouselImageUrls(List<String> carouselImageUrls) { this.carouselImageUrls = carouselImageUrls; }
    public ProductSemiManagedReq getProductSemiManagedReq() { return productSemiManagedReq; }
    public void setProductSemiManagedReq(ProductSemiManagedReq productSemiManagedReq) { this.productSemiManagedReq = productSemiManagedReq; }
    public ProductWarehouseRouteReq getProductWarehouseRouteReq() { return productWarehouseRouteReq; }
    public void setProductWarehouseRouteReq(ProductWarehouseRouteReq productWarehouseRouteReq) { this.productWarehouseRouteReq = productWarehouseRouteReq; }
    public ProductWhExtAttrReq getProductWhExtAttrReq() { return productWhExtAttrReq; }
    public void setProductWhExtAttrReq(ProductWhExtAttrReq productWhExtAttrReq) { this.productWhExtAttrReq = productWhExtAttrReq; }
    public ProductShipmentReq getProductShipmentReq() { return productShipmentReq; }
    public void setProductShipmentReq(ProductShipmentReq productShipmentReq) { this.productShipmentReq = productShipmentReq; }
    public List<ProductPropertyReq> getProductPropertyReqs() { return productPropertyReqs; }
    public void setProductPropertyReqs(List<ProductPropertyReq> productPropertyReqs) { this.productPropertyReqs = productPropertyReqs; }
    public List<ProductSkuReq> getProductSkuReqs() { return productSkuReqs; }
    public void setProductSkuReqs(List<ProductSkuReq> productSkuReqs) { this.productSkuReqs = productSkuReqs; }
    public ProductSkcReq getProductSkcReq() { return productSkcReq; }
    public void setProductSkcReq(ProductSkcReq productSkcReq) { this.productSkcReq = productSkcReq; }
    public List<ProductSpecPropertyReq> getProductSpecPropertyReqs() { return productSpecPropertyReqs; }
    public void setProductSpecPropertyReqs(List<ProductSpecPropertyReq> productSpecPropertyReqs) { this.productSpecPropertyReqs = productSpecPropertyReqs; }
    public Integer getSizeTemplateId() { return sizeTemplateId; }
    public void setSizeTemplateId(Integer sizeTemplateId) { this.sizeTemplateId = sizeTemplateId; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    public static class ProductI18nReq {
        private String lang;
        private String productName;
        public ProductI18nReq() {}
        public ProductI18nReq(String lang, String productName) { this.lang = lang; this.productName = productName; }
        public String getLang() { return lang; }
        public void setLang(String lang) { this.lang = lang; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
    }

    public static class ProductSemiManagedReq {
        private Object bindUserIds;
        private List<Integer> bindSiteIds;
        private Object itemModel;
        private Object other;
        public ProductSemiManagedReq() {}
        public ProductSemiManagedReq(Object bindUserIds, List<Integer> bindSiteIds, Object itemModel, Object other) {
            this.bindUserIds = bindUserIds;
            this.bindSiteIds = bindSiteIds;
            this.itemModel = itemModel;
            this.other = other;
        }
        public List<Integer> getBindSiteIds() { return bindSiteIds; }
        public void setBindSiteIds(List<Integer> bindSiteIds) { this.bindSiteIds = bindSiteIds; }
    }

    public static class ProductWarehouseRouteReq {
        private List<RouteItem> targetRouteList;
        public List<RouteItem> getTargetRouteList() { return targetRouteList; }
        public void setTargetRouteList(List<RouteItem> targetRouteList) { this.targetRouteList = targetRouteList; }

        public static class RouteItem {
            private List<Integer> siteIdList;
            private String warehouseId;
            public RouteItem() {}
            public RouteItem(List<Integer> siteIdList, String warehouseId) { this.siteIdList = siteIdList; this.warehouseId = warehouseId; }
            public List<Integer> getSiteIdList() { return siteIdList; }
            public void setSiteIdList(List<Integer> siteIdList) { this.siteIdList = siteIdList; }
            public String getWarehouseId() { return warehouseId; }
            public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        }
    }

    public static class ProductWhExtAttrReq {
        private String outerGoodsUrl;
        private ProductOrigin productOrigin;
        public String getOuterGoodsUrl() { return outerGoodsUrl; }
        public void setOuterGoodsUrl(String outerGoodsUrl) { this.outerGoodsUrl = outerGoodsUrl; }
        public ProductOrigin getProductOrigin() { return productOrigin; }
        public void setProductOrigin(ProductOrigin productOrigin) { this.productOrigin = productOrigin; }
    }

    public static class ProductOrigin {
        private String region1ShortName;
        private Long region2Id;
        public String getRegion1ShortName() { return region1ShortName; }
        public void setRegion1ShortName(String region1ShortName) { this.region1ShortName = region1ShortName; }
        public Long getRegion2Id() { return region2Id; }
        public void setRegion2Id(Long region2Id) { this.region2Id = region2Id; }
    }

    public static class ProductShipmentReq {
        private String freightTemplateId;
        private Integer shipmentLimitSecond;
        public String getFreightTemplateId() { return freightTemplateId; }
        public void setFreightTemplateId(String freightTemplateId) { this.freightTemplateId = freightTemplateId; }
        public Integer getShipmentLimitSecond() { return shipmentLimitSecond; }
        public void setShipmentLimitSecond(Integer shipmentLimitSecond) { this.shipmentLimitSecond = shipmentLimitSecond; }
    }

    public static class ProductPropertyReq {
        private Integer vid;
        private String valueUnit;
        private Integer pid;
        private Integer templatePid;
        private String numberInputValue;
        private String propValue;
        private String propName;
        private Integer refPid;
        public Integer getVid() { return vid; }
        public void setVid(Integer vid) { this.vid = vid; }
        public String getValueUnit() { return valueUnit; }
        public void setValueUnit(String valueUnit) { this.valueUnit = valueUnit; }
        public Integer getPid() { return pid; }
        public void setPid(Integer pid) { this.pid = pid; }
        public Integer getTemplatePid() { return templatePid; }
        public void setTemplatePid(Integer templatePid) { this.templatePid = templatePid; }
        public String getNumberInputValue() { return numberInputValue; }
        public void setNumberInputValue(String numberInputValue) { this.numberInputValue = numberInputValue; }
        public String getPropValue() { return propValue; }
        public void setPropValue(String propValue) { this.propValue = propValue; }
        public String getPropName() { return propName; }
        public void setPropName(String propName) { this.propName = propName; }
        public Integer getRefPid() { return refPid; }
        public void setRefPid(Integer refPid) { this.refPid = refPid; }
    }

    public static class ProductSpecPropertyReq {
        private Integer vid;
        private Integer specId;
        private Integer valueGroupId;
        private Integer parentSpecId;
        private String valueGroupName;
        private String valueUnit;
        private Integer pid;
        private Integer templatePid;
        private String numberInputValue;
        private String propValue;
        private String propName;
        private Integer refPid;

        public Integer getVid() { return vid; }
        public void setVid(Integer vid) { this.vid = vid; }
        public Integer getSpecId() { return specId; }
        public void setSpecId(Integer specId) { this.specId = specId; }
        public Integer getValueGroupId() { return valueGroupId; }
        public void setValueGroupId(Integer valueGroupId) { this.valueGroupId = valueGroupId; }
        public Integer getParentSpecId() { return parentSpecId; }
        public void setParentSpecId(Integer parentSpecId) { this.parentSpecId = parentSpecId; }
        public String getValueGroupName() { return valueGroupName; }
        public void setValueGroupName(String valueGroupName) { this.valueGroupName = valueGroupName; }
        public String getValueUnit() { return valueUnit; }
        public void setValueUnit(String valueUnit) { this.valueUnit = valueUnit; }
        public Integer getPid() { return pid; }
        public void setPid(Integer pid) { this.pid = pid; }
        public Integer getTemplatePid() { return templatePid; }
        public void setTemplatePid(Integer templatePid) { this.templatePid = templatePid; }
        public String getNumberInputValue() { return numberInputValue; }
        public void setNumberInputValue(String numberInputValue) { this.numberInputValue = numberInputValue; }
        public String getPropValue() { return propValue; }
        public void setPropValue(String propValue) { this.propValue = propValue; }
        public String getPropName() { return propName; }
        public void setPropName(String propName) { this.propName = propName; }
        public Integer getRefPid() { return refPid; }
        public void setRefPid(Integer refPid) { this.refPid = refPid; }
    }

    public static class ProductSkuReq {
        private String currencyType;
        private List<SiteSupplierPrice> siteSupplierPrices;
        private ProductSkuStockQuantityReq productSkuStockQuantityReq;
        private String thumbUrl;
        private ProductSkuWhExtAttrReq productSkuWhExtAttrReq;
        private ProductSkuWeightReq productSkuWeightReq;
        private ProductSkuVolumeReq productSkuVolumeReq;
        private String extCode;
        private List<ProductSkuSpecReq> productSkuSpecReqs;
        private List<ProductSkuSpecReq> mainProductSkuSpecReqs;
        private Integer specId;

        public void setCurrencyType(String currencyType) { this.currencyType = currencyType; }
        public String getCurrencyType() { return currencyType; }
        public List<SiteSupplierPrice> getSiteSupplierPrices() { return siteSupplierPrices; }
        public void setSiteSupplierPrices(List<SiteSupplierPrice> siteSupplierPrices) { this.siteSupplierPrices = siteSupplierPrices; }
        public ProductSkuStockQuantityReq getProductSkuStockQuantityReq() { return productSkuStockQuantityReq; }
        public void setProductSkuStockQuantityReq(ProductSkuStockQuantityReq productSkuStockQuantityReq) { this.productSkuStockQuantityReq = productSkuStockQuantityReq; }
        public String getThumbUrl() { return thumbUrl; }
        public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }
        public ProductSkuWhExtAttrReq getProductSkuWhExtAttrReq() { return productSkuWhExtAttrReq; }
        public void setProductSkuWhExtAttrReq(ProductSkuWhExtAttrReq productSkuWhExtAttrReq) { this.productSkuWhExtAttrReq = productSkuWhExtAttrReq; }
        public ProductSkuWeightReq getProductSkuWeightReq() { return productSkuWeightReq; }
        public void setProductSkuWeightReq(ProductSkuWeightReq productSkuWeightReq) { this.productSkuWeightReq = productSkuWeightReq; }
        public ProductSkuVolumeReq getProductSkuVolumeReq() { return productSkuVolumeReq; }
        public void setProductSkuVolumeReq(ProductSkuVolumeReq productSkuVolumeReq) { this.productSkuVolumeReq = productSkuVolumeReq; }
        public String getExtCode() { return extCode; }
        public void setExtCode(String extCode) { this.extCode = extCode; }
        public List<ProductSkuSpecReq> getProductSkuSpecReqs() { return productSkuSpecReqs; }
        public void setProductSkuSpecReqs(List<ProductSkuSpecReq> productSkuSpecReqs) { this.productSkuSpecReqs = productSkuSpecReqs; }
        public List<ProductSkuSpecReq> getMainProductSkuSpecReqs() { return mainProductSkuSpecReqs; }
        public void setMainProductSkuSpecReqs(List<ProductSkuSpecReq> mainProductSkuSpecReqs) { this.mainProductSkuSpecReqs = mainProductSkuSpecReqs; }
        public Integer getSpecId() { return specId; }
        public void setSpecId(Integer specId) { this.specId = specId; }

        public static class SiteSupplierPrice {
            private Integer siteId;
            private Integer supplierPrice;
            public SiteSupplierPrice() {}
            public SiteSupplierPrice(Integer siteId, Integer supplierPrice) { this.siteId = siteId; this.supplierPrice = supplierPrice; }
            public Integer getSiteId() { return siteId; }
            public void setSiteId(Integer siteId) { this.siteId = siteId; }
            public Integer getSupplierPrice() { return supplierPrice; }
            public void setSupplierPrice(Integer supplierPrice) { this.supplierPrice = supplierPrice; }
        }

        public static class ProductSkuStockQuantityReq {
            private List<WarehouseStockQuantityReq> warehouseStockQuantityReqs;
            public List<WarehouseStockQuantityReq> getWarehouseStockQuantityReqs() { return warehouseStockQuantityReqs; }
            public void setWarehouseStockQuantityReqs(List<WarehouseStockQuantityReq> warehouseStockQuantityReqs) { this.warehouseStockQuantityReqs = warehouseStockQuantityReqs; }

            public static class WarehouseStockQuantityReq {
                private Integer stockQuantity;
                private String warehouseId;
                private Object sellerSku;
                public WarehouseStockQuantityReq() {}
                public WarehouseStockQuantityReq(Integer stockQuantity, String warehouseId, Object sellerSku) {
                    this.stockQuantity = stockQuantity;
                    this.warehouseId = warehouseId;
                    this.sellerSku = sellerSku;
                }
                public Integer getStockQuantity() { return stockQuantity; }
                public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
                public String getWarehouseId() { return warehouseId; }
                public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
            }
        }

        public static class ProductSkuWhExtAttrReq {
            private Map<String, Object> data;
            public Map<String, Object> getData() { return data; }
            public void setData(Map<String, Object> data) { this.data = data; }
        }

        public static class ProductSkuWeightReq {
            private Integer value;
            public Integer getValue() { return value; }
            public void setValue(Integer value) { this.value = value; }
        }

        public static class ProductSkuVolumeReq {
            private Integer len;
            private Integer width;
            private Integer height;
            public Integer getLen() { return len; }
            public void setLen(Integer len) { this.len = len; }
            public Integer getWidth() { return width; }
            public void setWidth(Integer width) { this.width = width; }
            public Integer getHeight() { return height; }
            public void setHeight(Integer height) { this.height = height; }
        }

        public static class ProductSkuSpecReq {
            private Integer parentSpecId;
            private Integer specId;
            private String parentSpecName;
            private String specName;
            public Integer getParentSpecId() { return parentSpecId; }
            public void setParentSpecId(Integer parentSpecId) { this.parentSpecId = parentSpecId; }
            public Integer getSpecId() { return specId; }
            public void setSpecId(Integer specId) { this.specId = specId; }
            public String getParentSpecName() { return parentSpecName; }
            public void setParentSpecName(String parentSpecName) { this.parentSpecName = parentSpecName; }
            public String getSpecName() { return specName; }
            public void setSpecName(String specName) { this.specName = specName; }
        }
    }

    public static class ProductSkcReq {
        private String extCode;
        private List<MainProductSkuSpecReq> mainProductSkuSpecReqs;
        public String getExtCode() { return extCode; }
        public void setExtCode(String extCode) { this.extCode = extCode; }
        public List<MainProductSkuSpecReq> getMainProductSkuSpecReqs() { return mainProductSkuSpecReqs; }
        public void setMainProductSkuSpecReqs(List<MainProductSkuSpecReq> mainProductSkuSpecReqs) { this.mainProductSkuSpecReqs = mainProductSkuSpecReqs; }

        public static class MainProductSkuSpecReq {
            private Integer parentSpecId;
            private Integer specId;
            public Integer getParentSpecId() { return parentSpecId; }
            public void setParentSpecId(Integer parentSpecId) { this.parentSpecId = parentSpecId; }
            public Integer getSpecId() { return specId; }
            public void setSpecId(Integer specId) { this.specId = specId; }
        }
    }
}
