package com.tminos.productscene.service;

import com.tminos.productscene.dto.ProductDashboardDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductDashboardService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String PUBLISH_SUCCESS_SERIES_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            stats as (
                select
                    cast(temu_published_at as date) as day,
                    cast(count(*) as bigint) as value
                from product_collection
                where coalesce(temu_published, false) = true
                  and temu_published_at >= cast(:startDate as timestamp)
                  and temu_published_at < cast(:endExclusiveDate as timestamp)
                group by 1
            )
            select days.day, coalesce(stats.value, 0) as value
            from days
            left join stats on stats.day = days.day
            order by days.day
            """;

    private static final String TOTAL_COLLECTED_SQL = """
            select cast(count(*) as bigint)
            from product_collection
            """;

    private static final String TOTAL_PUBLISHED_SQL = """
            select cast(count(*) as bigint)
            from product_collection
            where coalesce(temu_published, false) = true
              and temu_published_at is not null
            """;

    private static final String TOTAL_ADDED_SITE_SQL = """
            select cast(count(distinct pc.id) as bigint)
            from product_collection pc
            join product_collection_temu_sku pcts on pcts.spu_id = pc.id
            join temu_goods_sku tgs
              on (pcts.temu_sku_id is not null and btrim(pcts.temu_sku_id) <> '' and tgs.ext_code = pcts.temu_sku_id)
              or (pcts.origin_sku_id is not null and btrim(pcts.origin_sku_id) <> '' and tgs.ext_code = pcts.origin_sku_id)
            join temu_goods tg on tg.id = tgs.goods_id
            where coalesce(pc.temu_published, false) = true
              and pc.temu_published_at is not null
              and coalesce(tg.skc_site_status, 0) = 1
            """;

    private static final String PRICE_REVIEW_APPROVE_SERIES_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            stats as (
                select
                    cast(review_at as date) as day,
                    cast(count(*) as bigint) as value
                from temu_price_review_order
                where review_at >= cast(:startDate as timestamp)
                  and review_at < cast(:endExclusiveDate as timestamp)
                  and upper(coalesce(review_action, '')) in ('APPROVE', 'APPROVED')
                group by 1
            )
            select days.day, coalesce(stats.value, 0) as value
            from days
            left join stats on stats.day = days.day
            order by days.day
            """;

    private static final String PRICE_REVIEW_REJECT_SERIES_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            stats as (
                select
                    cast(review_at as date) as day,
                    cast(count(*) as bigint) as value
                from temu_price_review_order
                where review_at >= cast(:startDate as timestamp)
                  and review_at < cast(:endExclusiveDate as timestamp)
                  and upper(coalesce(review_action, '')) in ('REJECT', 'REJECTED')
                group by 1
            )
            select days.day, coalesce(stats.value, 0) as value
            from days
            left join stats on stats.day = days.day
            order by days.day
            """;

    private static final String PUBLISHED_PRODUCT_PRICE_REVIEW_STATUS_SERIES_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            published_products as (
                select
                    pc.id as spu_id,
                    cast(pc.temu_published_at as date) as published_day
                from product_collection pc
                where coalesce(pc.temu_published, false) = true
                  and pc.temu_published_at >= cast(:startDate as timestamp)
                  and pc.temu_published_at < cast(:endExclusiveDate as timestamp)
            ),
            published_product_skus as (
                select distinct
                    pp.spu_id,
                    pp.published_day,
                    tgs.product_sku_id,
                    tgs.goods_id
                from published_products pp
                join product_collection_temu_sku pcts on pcts.spu_id = pp.spu_id
                join temu_goods_sku tgs on tgs.ext_code = pcts.temu_sku_id
                where pcts.temu_sku_id is not null and btrim(pcts.temu_sku_id) <> ''
                union
                select distinct
                    pp.spu_id,
                    pp.published_day,
                    tgs.product_sku_id,
                    tgs.goods_id
                from published_products pp
                join product_collection_temu_sku pcts on pcts.spu_id = pp.spu_id
                join temu_goods_sku tgs on tgs.ext_code = pcts.origin_sku_id
                where pcts.origin_sku_id is not null and btrim(pcts.origin_sku_id) <> ''
            ),
            product_flags as (
                select
                    pp.spu_id,
                    pp.published_day,
                    count(distinct pps.product_sku_id) as sku_count,
                    count(distinct case when pro.id is not null then pps.product_sku_id end) as reviewed_sku_count,
                    count(distinct case when upper(coalesce(pro.review_action, '')) in ('APPROVE', 'APPROVED') then pps.product_sku_id end) as approved_sku_count,
                    count(distinct case when upper(coalesce(pro.review_action, '')) in ('REJECT', 'REJECTED') then pps.product_sku_id end) as rejected_sku_count,
                    max(case when coalesce(tg.skc_site_status, 0) = 1 then 1 else 0 end) as added_site_flag
                from published_products pp
                left join published_product_skus pps on pps.spu_id = pp.spu_id
                left join temu_price_review_sku prs on prs.product_sku_id = pps.product_sku_id
                left join temu_price_review_order pro on pro.id = prs.review_order_id
                left join temu_goods tg on tg.id = pps.goods_id
                group by pp.spu_id, pp.published_day
            ),
            stats as (
                select
                    published_day as day,
                    cast(count(*) filter (where approved_sku_count > 0) as bigint) as approved_value,
                    cast(count(*) filter (where approved_sku_count = 0 and sku_count > 0 and rejected_sku_count >= sku_count) as bigint) as rejected_value,
                    cast(count(*) filter (where reviewed_sku_count = 0) as bigint) as unreviewed_value,
                    cast(count(*) filter (where added_site_flag = 1) as bigint) as added_site_value
                from product_flags
                group by published_day
            )
            select
                days.day,
                coalesce(stats.approved_value, 0) as approved_value,
                coalesce(stats.rejected_value, 0) as rejected_value,
                coalesce(stats.unreviewed_value, 0) as unreviewed_value,
                coalesce(stats.added_site_value, 0) as added_site_value
            from days
            left join stats on stats.day = days.day
            order by days.day
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductDashboardDTO.Response getDashboard(Integer days) {
        int resolvedDays = Math.min(Math.max(days == null ? 30 : days, 1), 90);
        LocalDate today = LocalDate.now(ZONE_ID);
        LocalDate startDate = today.minusDays(resolvedDays - 1L);
        LocalDate endDate = today;

        List<ProductDashboardDTO.DailyValue> recentPublishSuccessSeries = queryDailyValues(PUBLISH_SUCCESS_SERIES_SQL, startDate, endDate);
        List<ProductDashboardDTO.DailyValue> recentPriceReviewApproveSeries = queryDailyValues(PRICE_REVIEW_APPROVE_SERIES_SQL, startDate, endDate);
        List<ProductDashboardDTO.DailyValue> recentPriceReviewRejectSeries = queryDailyValues(PRICE_REVIEW_REJECT_SERIES_SQL, startDate, endDate);
        PublishedProductPriceReviewStatusSeries publishedProductPriceReviewStatusSeries =
                queryPublishedProductPriceReviewStatusSeries(startDate, endDate);

        return ProductDashboardDTO.Response.builder()
                .recent30Window(toWindow(startDate, endDate, resolvedDays))
                .totals(ProductDashboardDTO.Totals.builder()
                        .totalCollectedCount(querySingleValue(TOTAL_COLLECTED_SQL))
                        .totalPublishedCount(querySingleValue(TOTAL_PUBLISHED_SQL))
                        .totalAddedSiteCount(querySingleValue(TOTAL_ADDED_SITE_SQL))
                        .recentPublishedCount(sumValues(recentPublishSuccessSeries))
                        .recentAddedSiteCount(sumValues(publishedProductPriceReviewStatusSeries.addedSiteSeries()))
                        .todayPublishedCount(getLastDailyValue(recentPublishSuccessSeries))
                        .todayAddedSiteCount(getLastDailyValue(publishedProductPriceReviewStatusSeries.addedSiteSeries()))
                        .build())
                .recentPublishSuccessSeries(recentPublishSuccessSeries)
                .recentPriceReviewApproveSeries(recentPriceReviewApproveSeries)
                .recentPriceReviewRejectSeries(recentPriceReviewRejectSeries)
                .recentPublishedProductPriceReviewApprovedSeries(publishedProductPriceReviewStatusSeries.approvedSeries())
                .recentPublishedProductPriceReviewRejectedSeries(publishedProductPriceReviewStatusSeries.rejectedSeries())
                .recentPublishedProductPriceReviewUnreviewedSeries(publishedProductPriceReviewStatusSeries.unreviewedSeries())
                .recentPublishedProductAddedSiteSeries(publishedProductPriceReviewStatusSeries.addedSiteSeries())
                .build();
    }

    private PublishedProductPriceReviewStatusSeries queryPublishedProductPriceReviewStatusSeries(LocalDate startDate, LocalDate endDate) {
        Query query = entityManager.createNativeQuery(PUBLISHED_PRODUCT_PRICE_REVIEW_STATUS_SERIES_SQL);
        query.setParameter("startDate", Date.valueOf(startDate));
        query.setParameter("endDate", Date.valueOf(endDate));
        query.setParameter("endExclusiveDate", Date.valueOf(endDate.plusDays(1)));
        List<?> rows = query.getResultList();
        List<ProductDashboardDTO.DailyValue> approvedSeries = new ArrayList<>();
        List<ProductDashboardDTO.DailyValue> rejectedSeries = new ArrayList<>();
        List<ProductDashboardDTO.DailyValue> unreviewedSeries = new ArrayList<>();
        List<ProductDashboardDTO.DailyValue> addedSiteSeries = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            String date = formatDate(values[0]);
            approvedSeries.add(ProductDashboardDTO.DailyValue.builder().date(date).value(toLong(values[1])).build());
            rejectedSeries.add(ProductDashboardDTO.DailyValue.builder().date(date).value(toLong(values[2])).build());
            unreviewedSeries.add(ProductDashboardDTO.DailyValue.builder().date(date).value(toLong(values[3])).build());
            addedSiteSeries.add(ProductDashboardDTO.DailyValue.builder().date(date).value(toLong(values[4])).build());
        }
        return new PublishedProductPriceReviewStatusSeries(approvedSeries, rejectedSeries, unreviewedSeries, addedSiteSeries);
    }

    private List<ProductDashboardDTO.DailyValue> queryDailyValues(String sql, LocalDate startDate, LocalDate endDate) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("startDate", Date.valueOf(startDate));
        query.setParameter("endDate", Date.valueOf(endDate));
        query.setParameter("endExclusiveDate", Date.valueOf(endDate.plusDays(1)));
        List<?> rows = query.getResultList();
        List<ProductDashboardDTO.DailyValue> out = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            out.add(ProductDashboardDTO.DailyValue.builder()
                    .date(formatDate(values[0]))
                    .value(toLong(values[1]))
                    .build());
        }
        return out;
    }

    private long querySingleValue(String sql) {
        Query query = entityManager.createNativeQuery(sql);
        Object value = query.getSingleResult();
        return toLong(value);
    }

    private ProductDashboardDTO.DateWindow toWindow(LocalDate startDate, LocalDate endDate, int days) {
        return ProductDashboardDTO.DateWindow.builder()
                .startDate(startDate.format(DATE_FORMATTER))
                .endDate(endDate.format(DATE_FORMATTER))
                .days(days)
                .label("最近" + days + "天")
                .build();
    }

    private long sumValues(List<ProductDashboardDTO.DailyValue> rows) {
        long total = 0L;
        for (ProductDashboardDTO.DailyValue row : rows) {
            total += row.getValue() == null ? 0L : row.getValue();
        }
        return total;
    }

    private long getLastDailyValue(List<ProductDashboardDTO.DailyValue> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        ProductDashboardDTO.DailyValue last = rows.get(rows.size() - 1);
        return last.getValue() == null ? 0L : last.getValue();
    }

    private String formatDate(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate().format(DATE_FORMATTER);
        }
        return String.valueOf(value);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private record PublishedProductPriceReviewStatusSeries(
            List<ProductDashboardDTO.DailyValue> approvedSeries,
            List<ProductDashboardDTO.DailyValue> rejectedSeries,
            List<ProductDashboardDTO.DailyValue> unreviewedSeries,
            List<ProductDashboardDTO.DailyValue> addedSiteSeries
    ) {
    }
}
