package com.tminos.productscene.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TemuAttrAiFillTaskClaimService {

    public record ClaimedTask(Long id) {
    }

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public ClaimedTask claimNextPending() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select id from temu_attr_ai_fill_tasks " +
                                "where status = 0 " +
                                "order by id asc " +
                                "for update skip locked " +
                                "limit 1")
                .getResultList();
        if (rows == null || rows.isEmpty() || rows.get(0) == null) return null;
        Object[] row = rows.get(0);
        if (row.length < 1 || row[0] == null) return null;
        Long id = ((Number) row[0]).longValue();

        int updated = em.createNativeQuery(
                        "update temu_attr_ai_fill_tasks " +
                                "set status = 1, started_at = now(), updated_at = now() " +
                                "where id = :id and status = 0")
                .setParameter("id", id)
                .executeUpdate();

        return updated == 1 ? new ClaimedTask(id) : null;
    }
}
