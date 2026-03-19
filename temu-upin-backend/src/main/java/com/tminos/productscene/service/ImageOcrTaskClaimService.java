package com.tminos.productscene.service;

import com.tminos.productscene.entity.ImageOcrTask;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL-safe task claiming for OCR tasks (multi-instance).
 */
@Service
public class ImageOcrTaskClaimService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public Long claimNextIdPreferPendingThenFailed() {
        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery(
                        "select id from image_ocr_task " +
                                "where (exec_status in (0, 3)) " +
                                "or (exec_status = 1 and task_finished_at is null and task_started_at is not null and task_started_at < (now() - interval '1 hour')) " +
                                "order by " +
                                "  case when exec_status = 0 then 0 when exec_status = 3 then 1 else 2 end, " +
                                "  coalesce(task_started_at, to_timestamp(0)) asc, " +
                                "  id asc " +
                                "for update skip locked " +
                                "limit 1")
                .getResultList();
        if (ids == null || ids.isEmpty() || ids.get(0) == null) return null;
        return ids.get(0).longValue();
    }
}
