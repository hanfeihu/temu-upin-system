package com.tminos.productscene.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class Alibaba1688SelectionAiReportTaskClaimService {

    private static final String STALE_RUNNING_INTERVAL = "30 minutes";
    private static final String CLAIMABLE_TASK_PREDICATE = """
            (
              coalesce(p.ai_selection_analysis_status, 'NOT_STARTED') = 'NOT_STARTED'
              or (coalesce(p.ai_selection_report_json, '') = '' and coalesce(p.ai_selection_analysis_status, 'NOT_STARTED') not in ('FAILED', 'RUNNING'))
              or (p.ai_selection_analysis_status = 'RUNNING' and p.updated_at < (now() - interval '%s'))
            )
            """.formatted(STALE_RUNNING_INTERVAL);
    private static final String EXCLUDE_FILTERED_CATEGORY_PREDICATE = """
            not exists (
              select 1
              from alibaba_1688_selection_pool_filter_categories c
              where c.enabled = true
                and c.category_name is not null
                and btrim(c.category_name) <> ''
                and lower(btrim(c.category_name)) = lower(btrim(coalesce(p.category_snapshot, '')))
            )
            """;

    public record ClaimedTask(Long poolId, String offerId) {
    }

    public record TaskStats(int pendingCount, int runningCount, int readyCount, int failedCount) {
    }

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public ClaimedTask claimNextPending() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        """
                        select p.id, p.offer_id
                        from alibaba_1688_selection_pools p
                        where
                        """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                          and
                        """ + CLAIMABLE_TASK_PREDICATE + """
                        order by p.id asc
                        for update skip locked
                        limit 1
                        """)
                .getResultList();
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        Object[] row = rows.get(0);
        Long poolId = row[0] == null ? null : ((Number) row[0]).longValue();
        String offerId = row.length > 1 && row[1] != null ? String.valueOf(row[1]) : null;
        if (poolId == null) {
            return null;
        }
        int updated = em.createNativeQuery(
                        """
                        update alibaba_1688_selection_pools p
                        set ai_selection_analysis_status = 'RUNNING',
                            ai_selection_analysis_summary = 'AI 报告生成中',
                            updated_at = now()
                        where p.id = :id
                          and
                        """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                          and
                        """ + CLAIMABLE_TASK_PREDICATE)
                .setParameter("id", poolId)
                .executeUpdate();
        return updated == 1 ? new ClaimedTask(poolId, offerId) : null;
    }

    @Transactional(readOnly = true)
    public TaskStats stats() {
        int pending = count("""
                select count(*)
                from alibaba_1688_selection_pools p
                where
                """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                  and
                """ + CLAIMABLE_TASK_PREDICATE
        );
        int running = count("""
                select count(*)
                from alibaba_1688_selection_pools p
                where
                """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                  and p.ai_selection_analysis_status = 'RUNNING'
                  and p.updated_at >= (now() - interval '30 minutes')
                """);
        int ready = count("""
                select count(*)
                from alibaba_1688_selection_pools p
                where
                """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                  and p.ai_selection_analysis_status = 'READY'
                  and coalesce(p.ai_selection_report_json, '') <> ''
                """);
        int failed = count("""
                select count(*)
                from alibaba_1688_selection_pools p
                where
                """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                  and p.ai_selection_analysis_status = 'FAILED'
                """);
        return new TaskStats(pending, running, ready, failed);
    }

    @Transactional
    public void markFailed(Long poolId, String errorMessage) {
        if (poolId == null) {
            return;
        }
        em.createNativeQuery(
                        "update alibaba_1688_selection_pools " +
                                "set ai_selection_analysis_status = 'FAILED', " +
                                "    ai_selection_analysis_summary = :errorMessage, " +
                                "    updated_at = now() " +
                                "where id = :id")
                .setParameter("id", poolId)
                .setParameter("errorMessage", truncate(errorMessage, 2000))
                .executeUpdate();
    }

    @Transactional
    public int retryFailed() {
        return em.createNativeQuery("""
                        update alibaba_1688_selection_pools p
                        set ai_selection_analysis_status = 'NOT_STARTED',
                            ai_selection_analysis_summary = null,
                            updated_at = now()
                        where
                        """ + EXCLUDE_FILTERED_CATEGORY_PREDICATE + """
                          and p.ai_selection_analysis_status = 'FAILED'
                        """)
                .executeUpdate();
    }

    private int count(String sql) {
        Object result = em.createNativeQuery(sql).getSingleResult();
        if (result instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String truncate(String value, int maxLength) {
        String text = value == null || value.isBlank() ? "AI 报告生成失败" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
