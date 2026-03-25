package com.tminos.productscene.dto;

import lombok.*;

import java.util.List;

public class TemuCategoryDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchCategoryRequest {
        private String title;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchCategoryResponse {
        private String requestId;
        private boolean success;
        private Integer errorCode;
        private String errorMsg;
        private List<CategoryPath> categoryPaths;
        private List<MatchOption> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchOption {
        // Full path formats required by frontend:
        // - pathIds: comma-separated catIds (e.g. "9711,11730,11731,11800")
        // - pathNames: "/" separated catNames (e.g. "家居、厨房用品/浴室用品/浴室配件/化妆品收纳盒")
        private String pathIds;
        private String pathNames;

        // Convenience leaf info (usually the last category)
        private String leafId;
        private String leafName;

        // Human readable path for display (" / " joined)
        private String pathText;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveTemuCategoryRequest {
        private String temuCatid;
        private String temuCatname;

        public String getTemuCatid() {
            return temuCatid;
        }

        public String getTemuCatname() {
            return temuCatname;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryPath {
        private CategoryNode cat1;
        private CategoryNode cat2;
        private CategoryNode cat3;
        private CategoryNode cat4;
        private CategoryNode cat5;
        private CategoryNode cat6;
        private CategoryNode cat7;
        private CategoryNode cat8;
        private CategoryNode cat9;
        private CategoryNode cat10;

        public CategoryNode leaf() {
            if (cat10 != null) return cat10;
            if (cat9 != null) return cat9;
            if (cat8 != null) return cat8;
            if (cat7 != null) return cat7;
            if (cat6 != null) return cat6;
            if (cat5 != null) return cat5;
            if (cat4 != null) return cat4;
            if (cat3 != null) return cat3;
            if (cat2 != null) return cat2;
            return cat1;
        }

        public String leafId() {
            CategoryNode leaf = leaf();
            return leaf == null ? null : String.valueOf(leaf.catId);
        }

        public String leafName() {
            CategoryNode leaf = leaf();
            return leaf == null ? null : leaf.catName;
        }

        public String pathText() {
            List<String> names = new java.util.ArrayList<>();
            if (cat1 != null && cat1.catName != null) names.add(cat1.catName);
            if (cat2 != null && cat2.catName != null) names.add(cat2.catName);
            if (cat3 != null && cat3.catName != null) names.add(cat3.catName);
            if (cat4 != null && cat4.catName != null) names.add(cat4.catName);
            if (cat5 != null && cat5.catName != null) names.add(cat5.catName);
            if (cat6 != null && cat6.catName != null) names.add(cat6.catName);
            if (cat7 != null && cat7.catName != null) names.add(cat7.catName);
            if (cat8 != null && cat8.catName != null) names.add(cat8.catName);
            if (cat9 != null && cat9.catName != null) names.add(cat9.catName);
            if (cat10 != null && cat10.catName != null) names.add(cat10.catName);
            return String.join(" / ", names);
        }

        public String pathNamesArrow() {
            List<String> names = new java.util.ArrayList<>();
            if (cat1 != null && cat1.catName != null) names.add(cat1.catName);
            if (cat2 != null && cat2.catName != null) names.add(cat2.catName);
            if (cat3 != null && cat3.catName != null) names.add(cat3.catName);
            if (cat4 != null && cat4.catName != null) names.add(cat4.catName);
            if (cat5 != null && cat5.catName != null) names.add(cat5.catName);
            if (cat6 != null && cat6.catName != null) names.add(cat6.catName);
            if (cat7 != null && cat7.catName != null) names.add(cat7.catName);
            if (cat8 != null && cat8.catName != null) names.add(cat8.catName);
            if (cat9 != null && cat9.catName != null) names.add(cat9.catName);
            if (cat10 != null && cat10.catName != null) names.add(cat10.catName);
            return String.join("/", names);
        }

        public String pathIdsCsv() {
            List<String> ids = new java.util.ArrayList<>();
            if (cat1 != null && cat1.catId != null) ids.add(String.valueOf(cat1.catId));
            if (cat2 != null && cat2.catId != null) ids.add(String.valueOf(cat2.catId));
            if (cat3 != null && cat3.catId != null) ids.add(String.valueOf(cat3.catId));
            if (cat4 != null && cat4.catId != null) ids.add(String.valueOf(cat4.catId));
            if (cat5 != null && cat5.catId != null) ids.add(String.valueOf(cat5.catId));
            if (cat6 != null && cat6.catId != null) ids.add(String.valueOf(cat6.catId));
            if (cat7 != null && cat7.catId != null) ids.add(String.valueOf(cat7.catId));
            if (cat8 != null && cat8.catId != null) ids.add(String.valueOf(cat8.catId));
            if (cat9 != null && cat9.catId != null) ids.add(String.valueOf(cat9.catId));
            if (cat10 != null && cat10.catId != null) ids.add(String.valueOf(cat10.catId));
            return String.join(",", ids);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryNode {
        private Long catId;
        private String catName;
        private Long parentCatId;
        private Integer catType;
        private Boolean isLeaf;
        private Integer hiddenType;
        private Integer catLevel;
        private Boolean isHidden;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParentSpecOption {
        private Integer parentSpecId;
        private String parentSpecName;
    }
}
