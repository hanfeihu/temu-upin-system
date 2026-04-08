package com.tminos.productscene.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL-safe task claiming for multi-instance workers.
 *
 * Uses FOR UPDATE SKIP LOCKED to ensure only one instance claims a row.
 */
@Service
public class PostImportTaskClaimService {

    private static final String STALE_RUNNING_INTERVAL = "15 minutes";

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public Long claimNextPendingId() {
        // Lock one pending row so other instances skip it.
        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery(
                        "select id from product_collection " +
                                "where ((exec_status = 0 or exec_status is null) " +
                                "or (exec_status = 1 and updated_at is not null and updated_at < (now() - interval '" + STALE_RUNNING_INTERVAL + "'))) " +
                                "and (ocr_status = 2) " +
                                "order by id asc " +
                                "for update skip locked " +
                                "limit 1")
                .getResultList();
        if (ids == null || ids.isEmpty() || ids.get(0) == null) {
            return null;
        }
        Long id = ids.get(0).longValue();

        int updated = em.createNativeQuery(
                        "update product_collection " +
                                "set exec_status = 1, exec_result = 'running', updated_at = now() " +
                                "where id = :id and ((exec_status = 0 or exec_status is null) " +
                                "or (exec_status = 1 and updated_at is not null and updated_at < (now() - interval '" + STALE_RUNNING_INTERVAL + "')))")
                .setParameter("id", id)
                .executeUpdate();
        return updated == 1 ? id : null;
    }
}
