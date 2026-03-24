package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuPublishSuccessCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuPublishSuccessCaseRepository extends JpaRepository<TemuPublishSuccessCase, Long> {

    Optional<TemuPublishSuccessCase> findByPublishRunId(Long publishRunId);

    List<TemuPublishSuccessCase> findTop100ByOrderByIdDesc();

    List<TemuPublishSuccessCase> findBySpuIdOrderByIdDesc(Long spuId);

    List<TemuPublishSuccessCase> findByTemuCatidOrderByIdDesc(String temuCatid);

    List<TemuPublishSuccessCase> findBySpuIdAndTemuCatidOrderByIdDesc(Long spuId, String temuCatid);
}