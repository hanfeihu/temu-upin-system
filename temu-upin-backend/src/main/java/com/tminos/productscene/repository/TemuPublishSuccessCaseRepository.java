package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuPublishSuccessCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuPublishSuccessCaseRepository extends JpaRepository<TemuPublishSuccessCase, Long> {

    Optional<TemuPublishSuccessCase> findByPublishRunId(Long publishRunId);

    Page<TemuPublishSuccessCase> findAllByOrderByIdDesc(Pageable pageable);

    Page<TemuPublishSuccessCase> findBySpuIdOrderByIdDesc(Long spuId, Pageable pageable);

    Page<TemuPublishSuccessCase> findByTemuCatidOrderByIdDesc(String temuCatid, Pageable pageable);

    Page<TemuPublishSuccessCase> findBySpuIdAndTemuCatidOrderByIdDesc(Long spuId, String temuCatid, Pageable pageable);

    List<TemuPublishSuccessCase> findTop100ByOrderByIdDesc();

    List<TemuPublishSuccessCase> findBySpuIdOrderByIdDesc(Long spuId);

    List<TemuPublishSuccessCase> findByTemuCatidOrderByIdDesc(String temuCatid);

    List<TemuPublishSuccessCase> findBySpuIdAndTemuCatidOrderByIdDesc(Long spuId, String temuCatid);
}