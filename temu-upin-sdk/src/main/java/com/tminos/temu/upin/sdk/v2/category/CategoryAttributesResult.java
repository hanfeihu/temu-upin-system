package com.tminos.temu.upin.sdk.v2.category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryAttributesResult {
    private Integer inputMaxSpecNum;
    private Boolean chooseAllQualifySpec;
    private Integer singleSpecValueNum;
    private List<Property> properties;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Property {
        private String numberInputTitle;
        private List<TemplatePropertyValueParent> templatePropertyValueParentList;
        private List<Value> values;
        private List<String> valueUnit;
        private Integer referenceType;
        private Integer pid;
        private Integer templatePid;
        private Boolean required;
        private Integer inputMaxNum;
        private Integer propertyValueType;
        private String minValue;
        private Integer feature;
        private Integer valueRule;
        private String propertyChooseTitle;
        private Integer showType;
        private Integer parentTemplatePid;
        private Boolean mainSale;
        private Integer parentSpecId;
        private String maxValue;
        private Map<String, String> lang2Name;
        private Integer chooseMaxNum;
        private Integer valuePrecision;
        private List<ShowCondition> showCondition;
        private Integer controlType;
        private String name;
        private Boolean isSale;
        private Integer refPid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplatePropertyValueParent {
        private List<Integer> parentVidList;
        private List<Integer> vidList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Value {
        private Integer vid;
        private Integer specId;
        private Map<String, String> lang2Value;
        private List<Integer> parentVidList;
        private String extendInfo;
        private String value;
        private Group group;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private String name;
        private Integer id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShowCondition {
        private Integer parentRefPid;
        private List<Integer> parentVids;
    }
}
