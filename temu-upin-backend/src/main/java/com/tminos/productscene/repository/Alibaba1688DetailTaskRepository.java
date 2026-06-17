package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688DetailTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface Alibaba1688DetailTaskRepository extends JpaRepository<Alibaba1688DetailTask, Long>, JpaSpecificationExecutor<Alibaba1688DetailTask> {

    List<Alibaba1688DetailTask> findAllByCredentialIdAndOfferIdIn(
            Long credentialId,
            Collection<String> offerIds
    );

    List<Alibaba1688DetailTask> findAllByCredentialIdAndOfferIdInAndStatusIn(
            Long credentialId,
            Collection<String> offerIds,
            Collection<String> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from Alibaba1688DetailTask t
            where t.status = 'PENDING'
              and (:credentialId is null or t.credentialId = :credentialId)
            order by t.id asc
            """)
    List<Alibaba1688DetailTask> lockPendingTasks(
            @Param("credentialId") Long credentialId,
            Pageable pageable
    );
}
