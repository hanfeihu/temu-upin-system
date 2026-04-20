package com.tminos.productscene.repository;

import com.tminos.productscene.entity.AiVariantPublishRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiVariantPublishRecordRepository extends JpaRepository<AiVariantPublishRecord, Long> {

    Page<AiVariantPublishRecord> findByShopRecordIdOrderByCreatedAtDesc(Long shopRecordId, Pageable pageable);
}
