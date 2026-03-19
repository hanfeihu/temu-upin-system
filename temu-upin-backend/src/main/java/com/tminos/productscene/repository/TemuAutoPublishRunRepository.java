package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuAutoPublishRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuAutoPublishRunRepository extends JpaRepository<TemuAutoPublishRun, Long>, JpaSpecificationExecutor<TemuAutoPublishRun> {
    List<TemuAutoPublishRun> findTop50ByOrderByIdDesc();
    List<TemuAutoPublishRun> findBySpuIdOrderByIdDesc(Long spuId);
}
