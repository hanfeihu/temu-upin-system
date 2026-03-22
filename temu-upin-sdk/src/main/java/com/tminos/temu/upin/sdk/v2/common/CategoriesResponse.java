package com.tminos.temu.upin.sdk.v2.common;
import java.util.List;

public class CategoriesResponse {
    private Boolean success;
    private Integer errorCode;
    private String errorMsg;
    private String requestId;
    private Result result;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public static class Result {
        private List<CategoryDTO> categoryDTOList;

        public List<CategoryDTO> getCategoryDTOList() {
            return categoryDTOList;
        }

        public void setCategoryDTOList(List<CategoryDTO> categoryDTOList) {
            this.categoryDTOList = categoryDTOList;
        }
    }

    public static class CategoryDTO {
        private Integer catId;
        private String catName;
        private Integer parentCatId;
        private Integer catType;
        private Boolean isLeaf;
        private Integer catLevel;
        private Boolean isHidden;
        private Integer hiddenType;

        public Integer getCatId() {
            return catId;
        }

        public void setCatId(Integer catId) {
            this.catId = catId;
        }

        public String getCatName() {
            return catName;
        }

        public void setCatName(String catName) {
            this.catName = catName;
        }

        public Integer getParentCatId() {
            return parentCatId;
        }

        public void setParentCatId(Integer parentCatId) {
            this.parentCatId = parentCatId;
        }

        public Integer getCatType() {
            return catType;
        }

        public void setCatType(Integer catType) {
            this.catType = catType;
        }

        public Boolean getIsLeaf() {
            return isLeaf;
        }

        public void setIsLeaf(Boolean isLeaf) {
            this.isLeaf = isLeaf;
        }

        public Integer getCatLevel() {
            return catLevel;
        }

        public void setCatLevel(Integer catLevel) {
            this.catLevel = catLevel;
        }

        public Boolean getIsHidden() {
            return isHidden;
        }

        public void setIsHidden(Boolean isHidden) {
            this.isHidden = isHidden;
        }

        public Integer getHiddenType() {
            return hiddenType;
        }

        public void setHiddenType(Integer hiddenType) {
            this.hiddenType = hiddenType;
        }
    }
}

