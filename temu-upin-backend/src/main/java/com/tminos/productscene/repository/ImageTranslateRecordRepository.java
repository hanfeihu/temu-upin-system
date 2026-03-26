package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ImageTranslateRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageTranslateRecordRepository extends JpaRepository<ImageTranslateRecord, Long>, JpaSpecificationExecutor<ImageTranslateRecord> {
}