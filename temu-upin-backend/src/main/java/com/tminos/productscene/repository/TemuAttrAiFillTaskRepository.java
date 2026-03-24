package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuAttrAiFillTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TemuAttrAiFillTaskRepository extends JpaRepository<TemuAttrAiFillTask, Long> {

        Optional<TemuAttrAiFillTask> findFirstBySpuIdOrderByIdDesc(Long spuId);

    @Query(
            value = "select t from TemuAttrAiFillTask t " +
                    "where (:q is null or lower(coalesce(t.productName, '')) like lower(concat('%', :q, '%')) or str(t.spuId) = :q) " +
                    "and (:status is null or t.status = :status) " +
                    "order by t.id desc"
    )
    Page<TemuAttrAiFillTask> search(@Param("q") String q,
                                    @Param("status") Integer status,
                                    Pageable pageable);
}
