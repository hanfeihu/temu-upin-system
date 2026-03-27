package com.tminos.temu.upin.sdk.v2.category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMandatoryRequest {
    private List<ProductPropertyReq> productPropertyReqs;
    private List<Integer> configItems;
    private Long leafCatId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPropertyReq {
        private Integer vid;
        private String valueUnit;
        private Integer pid;
        private Integer templatePid;
        private String numberInputValue;
        private String propValue;
        private String propName;
        private Integer refPid;
    }
}
