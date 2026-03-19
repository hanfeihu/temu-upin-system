package com.tminos.productscene.repository;

import com.tminos.productscene.entity.BizLogRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface BizLogRecordRepository extends JpaRepository<BizLogRecord, Long>, JpaSpecificationExecutor<BizLogRecord> {
    Page<BizLogRecord> findByBizName(String bizName, Pageable pageable);
}
