package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuPublishRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuPublishRunRepository extends JpaRepository<TemuPublishRun, Long> {
    List<TemuPublishRun> findBySpuIdOrderByIdDesc(Long spuId);

    List<TemuPublishRun> findTop50ByOrderByIdDesc();
}
