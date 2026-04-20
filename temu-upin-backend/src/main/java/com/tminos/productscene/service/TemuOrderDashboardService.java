package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuOrderDashboardDTO;
import com.tminos.productscene.entity.TemuShop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TemuOrderDashboardService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String RECENT_ORDER_COUNT_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            stats as (
                select
                    cast(timezone('Asia/Shanghai', to_timestamp(o.order_time_ms / 1000.0)) as date) as day,
                    cast(count(*) as bigint) as value
                from temu_orders o
                where o.shop_record_id = :shopRecordId
                  and o.order_time_ms >= :startMs
                  and o.order_time_ms < :endExclusiveMs
                  and coalesce(o.order_status, -1) <> 3
                group by 1
            )
            select days.day, coalesce(stats.value, 0) as value
            from days
            left join stats on stats.day = days.day
            order by days.day
            """;

    private static final String RECENT_QUANTITY_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            stats as (
                select
                    cast(timezone('Asia/Shanghai', to_timestamp(o.order_time_ms / 1000.0)) as date) as day,
                    cast(coalesce(sum(o.quantity), 0) as bigint) as value
                from temu_orders o
                where o.shop_record_id = :shopRecordId
                  and o.order_time_ms >= :startMs
                  and o.order_time_ms < :endExclusiveMs
                  and coalesce(o.order_status, -1) <> 3
                group by 1
            )
            select days.day, coalesce(stats.value, 0) as value
            from days
            left join stats on stats.day = days.day
            order by days.day
            """;

    private static final String HISTORICAL_SIGNED_AFTERSALE_SQL = """
            with days as (
                select cast(generate_series(cast(:startDate as date), cast(:endDate as date), interval '1 day') as date) as day
            ),
            parent_orders as (
                select
                    o.shop_record_id,
                    coalesce(nullif(btrim(o.parent_order_sn), ''), o.order_sn) as parent_key,
                    min(cast(timezone('Asia/Shanghai', to_timestamp(o.order_time_ms / 1000.0)) as date)) as order_day,
                    max(case when coalesce(o.parent_order_status, o.order_status) in (5, 51) then 1 else 0 end) as signed_flag
                from temu_orders o
                where o.shop_record_id = :shopRecordId
                  and o.order_time_ms >= :startMs
                  and o.order_time_ms < :endExclusiveMs
                  and coalesce(o.order_status, -1) <> 3
                group by o.shop_record_id, coalesce(nullif(btrim(o.parent_order_sn), ''), o.order_sn)
            ),
            aftersale_orders as (
                select distinct
                    a.shop_record_id,
                    coalesce(nullif(btrim(a.parent_order_sn), ''), a.parent_after_sales_sn) as parent_key
                from temu_order_aftersales a
                where a.shop_record_id = :shopRecordId
                  and coalesce(nullif(btrim(a.parent_order_sn), ''), a.parent_after_sales_sn) is not null
            )
            select
                days.day,
                cast(coalesce(sum(case when parent_orders.signed_flag = 1 then 1 else 0 end), 0) as bigint) as signed_value,
                cast(coalesce(sum(case when aftersale_orders.parent_key is not null then 1 else 0 end), 0) as bigint) as aftersale_value
            from days
            left join parent_orders on parent_orders.order_day = days.day
            left join aftersale_orders
                   on aftersale_orders.shop_record_id = parent_orders.shop_record_id
                  and aftersale_orders.parent_key = parent_orders.parent_key
            group by days.day
            order by days.day
            """;

    @PersistenceContext
    private EntityManager entityManager;

    private final TemuShopService shopService;

    public TemuOrderDashboardService(TemuShopService shopService) {
        this.shopService = shopService;
    }

    public TemuOrderDashboardDTO.Response getDashboard(Long shopRecordId, String shopId) {
        TemuShop shop = resolveShop(shopRecordId, shopId);

        LocalDate today = LocalDate.now(ZONE_ID);
        LocalDate recentStart = today.minusDays(29);
        LocalDate recentEnd = today;
        LocalDate historicalEnd = today.minusDays(15);
        LocalDate historicalStart = historicalEnd.minusDays(29);

        List<TemuOrderDashboardDTO.DailyValue> recentOrderCountSeries =
                queryDailyValues(RECENT_ORDER_COUNT_SQL, shop.getId(), recentStart, recentEnd);
        List<TemuOrderDashboardDTO.DailyValue> recentQuantitySeries =
                queryDailyValues(RECENT_QUANTITY_SQL, shop.getId(), recentStart, recentEnd);
        List<TemuOrderDashboardDTO.DailyCompareValue> historicalSignedAftersaleSeries =
                queryHistoricalCompareValues(shop.getId(), historicalStart, historicalEnd);

        return TemuOrderDashboardDTO.Response.builder()
                .shop(TemuOrderDashboardDTO.Shop.builder()
                        .shopRecordId(shop.getId())
                        .shopId(shop.getShopId())
                        .shopName(shop.getShopName())
                        .build())
                .recent30Window(toWindow(recentStart, recentEnd, "最近30天"))
                .historicalWindow(toWindow(historicalStart, historicalEnd, "15天前的30天"))
                .totals(TemuOrderDashboardDTO.Totals.builder()
                        .recentChildOrderCount(sumValues(recentOrderCountSeries))
                        .recentQuantity(sumValues(recentQuantitySeries))
                        .historicalSignedParentCount(sumSignedValues(historicalSignedAftersaleSeries))
                        .historicalAftersaleParentCount(sumAftersaleValues(historicalSignedAftersaleSeries))
                        .build())
                .recentOrderCountSeries(recentOrderCountSeries)
                .recentQuantitySeries(recentQuantitySeries)
                .historicalSignedAftersaleSeries(historicalSignedAftersaleSeries)
                .build();
    }

    private TemuShop resolveShop(Long shopRecordId, String shopId) {
        if (shopRecordId != null) {
            return shopService.getEnabledShopByIdOrThrow(shopRecordId);
        }
        if (StringUtils.hasText(shopId)) {
            return shopService.getEnabledShopByShopIdOrThrow(shopId);
        }
        return shopService.getAnyEnabledShopOrThrow();
    }

    private List<TemuOrderDashboardDTO.DailyValue> queryDailyValues(String sql,
                                                                    Long shopRecordId,
                                                                    LocalDate startDate,
                                                                    LocalDate endDate) {
        Query query = createSeriesQuery(sql, shopRecordId, startDate, endDate);
        List<?> rows = query.getResultList();
        List<TemuOrderDashboardDTO.DailyValue> out = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            out.add(TemuOrderDashboardDTO.DailyValue.builder()
                    .date(formatDate(values[0]))
                    .value(toLong(values[1]))
                    .build());
        }
        return out;
    }

    private List<TemuOrderDashboardDTO.DailyCompareValue> queryHistoricalCompareValues(Long shopRecordId,
                                                                                        LocalDate startDate,
                                                                                        LocalDate endDate) {
        Query query = createSeriesQuery(HISTORICAL_SIGNED_AFTERSALE_SQL, shopRecordId, startDate, endDate);
        List<?> rows = query.getResultList();
        List<TemuOrderDashboardDTO.DailyCompareValue> out = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            out.add(TemuOrderDashboardDTO.DailyCompareValue.builder()
                    .date(formatDate(values[0]))
                    .signedValue(toLong(values[1]))
                    .aftersaleValue(toLong(values[2]))
                    .build());
        }
        return out;
    }

    private Query createSeriesQuery(String sql, Long shopRecordId, LocalDate startDate, LocalDate endDate) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("shopRecordId", shopRecordId);
        query.setParameter("startDate", Date.valueOf(startDate));
        query.setParameter("endDate", Date.valueOf(endDate));
        query.setParameter("startMs", toStartMillis(startDate));
        query.setParameter("endExclusiveMs", toStartMillis(endDate.plusDays(1)));
        return query;
    }

    private TemuOrderDashboardDTO.DateWindow toWindow(LocalDate startDate, LocalDate endDate, String prefix) {
        return TemuOrderDashboardDTO.DateWindow.builder()
                .startDate(startDate.format(DATE_FORMATTER))
                .endDate(endDate.format(DATE_FORMATTER))
                .days((int) (endDate.toEpochDay() - startDate.toEpochDay() + 1))
                .label(prefix + "（" + startDate.format(DATE_FORMATTER) + " 至 " + endDate.format(DATE_FORMATTER) + "）")
                .build();
    }

    private long toStartMillis(LocalDate date) {
        return date.atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
    }

    private String formatDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate.format(DATE_FORMATTER);
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate().format(DATE_FORMATTER);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate().format(DATE_FORMATTER);
        }
        if (value instanceof java.util.Date utilDate) {
            return Instant.ofEpochMilli(utilDate.getTime()).atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
        }
        return String.valueOf(value);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private long sumValues(List<TemuOrderDashboardDTO.DailyValue> rows) {
        long total = 0L;
        for (TemuOrderDashboardDTO.DailyValue row : rows) {
            total += row == null || row.getValue() == null ? 0L : row.getValue();
        }
        return total;
    }

    private long sumSignedValues(List<TemuOrderDashboardDTO.DailyCompareValue> rows) {
        long total = 0L;
        for (TemuOrderDashboardDTO.DailyCompareValue row : rows) {
            total += row == null || row.getSignedValue() == null ? 0L : row.getSignedValue();
        }
        return total;
    }

    private long sumAftersaleValues(List<TemuOrderDashboardDTO.DailyCompareValue> rows) {
        long total = 0L;
        for (TemuOrderDashboardDTO.DailyCompareValue row : rows) {
            total += row == null || row.getAftersaleValue() == null ? 0L : row.getAftersaleValue();
        }
        return total;
    }
}
