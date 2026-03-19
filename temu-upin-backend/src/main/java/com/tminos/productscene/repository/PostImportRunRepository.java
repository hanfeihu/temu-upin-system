package com.tminos.productscene.repository;

import com.tminos.productscene.entity.PostImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImportRunRepository extends JpaRepository<PostImportRun, Long> {
    List<PostImportRun> findBySpuIdOrderByIdDesc(Long spuId);
    List<PostImportRun> findTop50ByOrderByIdDesc();
}
