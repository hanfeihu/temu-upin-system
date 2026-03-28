package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ParserTestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParserTestRunRepository extends JpaRepository<ParserTestRun, Long> {
    Optional<ParserTestRun> findByRunId(String runId);
}
