package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuSpecMappingDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemuSpecMappingDraftRepository extends JpaRepository<TemuSpecMappingDraft, Long> {
    Optional<TemuSpecMappingDraft> findFirstBySpuIdOrderByIdDesc(Long spuId);
    List<TemuSpecMappingDraft> findBySpuIdOrderByIdDesc(Long spuId);
}