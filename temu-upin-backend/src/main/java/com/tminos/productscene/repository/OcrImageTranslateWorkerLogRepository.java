package com.tminos.productscene.repository;

import com.tminos.productscene.entity.OcrImageTranslateWorkerLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OcrImageTranslateWorkerLogRepository extends JpaRepository<OcrImageTranslateWorkerLog, Long> {

    Page<OcrImageTranslateWorkerLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
