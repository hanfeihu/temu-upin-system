package com.tminos.productscene.repository;

import com.tminos.productscene.entity.PostImportRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PostImportRunRepository extends JpaRepository<PostImportRun, Long>, JpaSpecificationExecutor<PostImportRun> {
    List<PostImportRun> findBySpuIdOrderByIdDesc(Long spuId);
    List<PostImportRun> findTop50ByOrderByIdDesc();
}
