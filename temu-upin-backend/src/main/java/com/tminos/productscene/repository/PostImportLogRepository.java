package com.tminos.productscene.repository;

import com.tminos.productscene.entity.PostImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImportLogRepository extends JpaRepository<PostImportLog, Long> {
    List<PostImportLog> findByRunIdOrderByIdAsc(Long runId);
}
