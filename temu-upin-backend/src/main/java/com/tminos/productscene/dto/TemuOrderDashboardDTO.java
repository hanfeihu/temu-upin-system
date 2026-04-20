package com.tminos.productscene.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class TemuOrderDashboardDTO {

    @Data
    @Builder
    public static class Response {
        private Shop shop;
        private DateWindow recent30Window;
        private DateWindow historicalWindow;
        private Totals totals;
        private List<DailyValue> recentOrderCountSeries;
        private List<DailyValue> recentQuantitySeries;
        private List<DailyCompareValue> historicalSignedAftersaleSeries;
    }

    @Data
    @Builder
    public static class Shop {
        private Long shopRecordId;
        private String shopId;
        private String shopName;
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
        private Long recentChildOrderCount;
        private Long recentQuantity;
        private Long historicalSignedParentCount;
        private Long historicalAftersaleParentCount;
    }

    @Data
    @Builder
    public static class DailyValue {
        private String date;
        private Long value;
    }

    @Data
    @Builder
    public static class DailyCompareValue {
        private String date;
        private Long signedValue;
        private Long aftersaleValue;
    }
}
