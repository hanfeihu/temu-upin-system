package com.tminos.temu.upin.sdk.v2.category;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMandatoryResult {
    private Boolean needGuideFile;
    private List<ParentSpecOption> parentSpecOptions;
    private Map<String, Object> extra;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParentSpecOption {
        private Integer parentSpecId;
        private String parentSpecName;
        @SerializedName("extra")
        private Map<String, Object> extraFields;
    }
}
