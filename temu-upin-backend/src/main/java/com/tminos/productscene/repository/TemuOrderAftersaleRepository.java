package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuOrderAftersale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TemuOrderAftersaleRepository extends JpaRepository<TemuOrderAftersale, Long>, JpaSpecificationExecutor<TemuOrderAftersale> {

    Optional<TemuOrderAftersale> findByShopRecordIdAndParentAfterSalesSn(Long shopRecordId, String parentAfterSalesSn);

    List<TemuOrderAftersale> findByShopRecordIdAndAfterSalesStatusGroupInOrderByUpdateAtMsAscIdAsc(Long shopRecordId,
                                                                                                    Collection<Integer> afterSalesStatusGroups);
}
