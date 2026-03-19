package com.tminos.productscene.service;

import com.tminos.productscene.entity.ImageOcrTask;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OcrTaskInsertHelper {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void insertAll(List<ImageOcrTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        for (ImageOcrTask t : tasks) {
            if (t == null) continue;
            em.persist(t);
        }
        em.flush();
    }

    @Transactional
    public int deleteBySpuId(Long spuId) {
        if (spuId == null) return 0;
        return em.createNativeQuery("delete from image_ocr_task where spu_id = :spuId")
                .setParameter("spuId", spuId)
                .executeUpdate();
    }
}
