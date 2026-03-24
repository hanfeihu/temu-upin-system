package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuMainSaleSpecInferenceTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TemuMainSaleSpecInferenceTaskRepository extends JpaRepository<TemuMainSaleSpecInferenceTask, Long> {

        java.util.Optional<TemuMainSaleSpecInferenceTask> findFirstBySpuIdOrderByIdDesc(Long spuId);

    @Query(
            value = "select t from TemuMainSaleSpecInferenceTask t " +
                    "where (:q is null or lower(coalesce(t.productName, '')) like lower(concat('%', :q, '%')) or str(t.spuId) = :q) " +
                    "and (:status is null or t.status = :status) " +
                    "order by t.id desc"
    )
    Page<TemuMainSaleSpecInferenceTask> search(@Param("q") String q,
                                               @Param("status") Integer status,
                                               Pageable pageable);
}
