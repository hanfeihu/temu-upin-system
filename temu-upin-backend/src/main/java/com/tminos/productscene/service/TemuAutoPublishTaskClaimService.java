package com.tminos.productscene.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL-safe row claiming for auto-publish worker.
 *
 * We only claim rows that are likely publish candidates and mark them as "publishing" by
 * setting collection_status=1 (same as manual publish flow does initially).
 */
@Service
public class TemuAutoPublishTaskClaimService {

    public record ClaimedCandidate(Long spuId, Integer preClaimCollectionStatus) {
    }

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public ClaimedCandidate claimNextCandidate() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select id, collection_status from product_collection " +
                                "where deleted = false " +
                                "and (temu_published is null or temu_published = false) " +
                                "and (collection_status = 0 or collection_status is null) " +
                                "and exec_status = 2 " +
                                "and temu_catid is not null and btrim(temu_catid) <> '' " +
                                "and temu_attributes is not null and btrim(temu_attributes) <> '' " +
                                "order by updated_at asc " +
                                "for update skip locked " +
                                "limit 1")
                .getResultList();
        if (rows == null || rows.isEmpty() || rows.get(0) == null) return null;
        Object[] row = rows.get(0);
        if (row.length < 1 || row[0] == null) return null;
        Long id = ((Number) row[0]).longValue();
        Integer preStatus = row.length >= 2 && row[1] != null ? ((Number) row[1]).intValue() : null;

        int updated = em.createNativeQuery(
                        "update product_collection " +
                                "set collection_status = 1, updated_at = now() " +
                                "where id = :id " +
                                "and deleted = false " +
                                "and (temu_published is null or temu_published = false) " +
                                "and (collection_status = 0 or collection_status is null)")
                .setParameter("id", id)
                .executeUpdate();

        return updated == 1 ? new ClaimedCandidate(id, preStatus) : null;
    }
}
