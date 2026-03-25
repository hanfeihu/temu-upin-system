package com.tminos.productscene.service;

import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;

import java.util.List;

record StoredPublishSpecDraft(
        List<AddGloGoodsRequest.ProductSpecPropertyReq> productSpecPropertyReqs,
        List<List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq>> mainProductSkuSpecReqGroups,
        List<List<AddGloGoodsRequest.ProductSkuReq>> productSkuReqGroups
) {
}