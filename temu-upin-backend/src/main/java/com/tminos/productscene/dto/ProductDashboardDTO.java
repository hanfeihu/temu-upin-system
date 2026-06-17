package com.tminos.productscene.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class ProductDashboardDTO {

    @Data
    @Builder
    public static class Response {
        private DateWindow recent30Window;
        private Totals totals;
        private List<DailyValue> recentPublishSuccessSeries;
        private List<DailyValue> recentPriceReviewApproveSeries;
        private List<DailyValue> recentPriceReviewRejectSeries;
        private List<DailyValue> recentPublishedProductPriceReviewApprovedSeries;
        private List<DailyValue> recentPublishedProductPriceReviewRejectedSeries;
        private List<DailyValue> recentPublishedProductPriceReviewUnreviewedSeries;
        private List<DailyValue> recentPublishedProductAddedSiteSeries;
    }

    @Data
    @Builder
    public static class DateWindow {
        private String startDate;
        private String endDate;
        private Integer days;
        private String label;
    }

    @Data
    @Builder
    public static class Totals {
        private Long totalCollectedCount;
        private Long totalPublishedCount;
        private Long totalAddedSiteCount;
        private Long recentPublishedCount;
        private Long recentAddedSiteCount;
        private Long todayPublishedCount;
        private Long todayAddedSiteCount;
    }

    @Data
    @Builder
    public static class DailyValue {
        private String date;
        private Long value;
    }
}
