package com.tminos.productscene.repository;

import com.tminos.productscene.entity.DianxiaomiPackageFeeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DianxiaomiPackageFeeRecordRepository extends JpaRepository<DianxiaomiPackageFeeRecord, Long>, JpaSpecificationExecutor<DianxiaomiPackageFeeRecord> {

    Optional<DianxiaomiPackageFeeRecord> findByDianxiaomiPackageNumber(String dianxiaomiPackageNumber);

    @Query("""
            select r
            from DianxiaomiPackageFeeRecord r
            where r.errorMessage is not null
              and r.errorMessage <> ''
            order by r.updatedAt desc, r.id desc
            """)
    List<DianxiaomiPackageFeeRecord> findFailedRecords();

    @Query("""
            select count(r)
            from DianxiaomiPackageFeeRecord r
            where (:keyword is null or lower(r.dianxiaomiPackageNumber) like :keyword)
            """)
    long countByKeyword(@Param("keyword") String keyword);

    @Query("""
            select count(r)
            from DianxiaomiPackageFeeRecord r
            where (:keyword is null or lower(r.dianxiaomiPackageNumber) like :keyword)
              and r.totalFee is not null
              and (r.errorMessage is null or r.errorMessage = '')
            """)
    long countSuccessByKeyword(@Param("keyword") String keyword);

    @Query("""
            select count(r)
            from DianxiaomiPackageFeeRecord r
            where (:keyword is null or lower(r.dianxiaomiPackageNumber) like :keyword)
              and r.errorMessage is not null
              and r.errorMessage <> ''
            """)
    long countFailedByKeyword(@Param("keyword") String keyword);

    @Query("""
            select coalesce(sum(r.totalFee), 0)
            from DianxiaomiPackageFeeRecord r
            where (:keyword is null or lower(r.dianxiaomiPackageNumber) like :keyword)
            """)
    BigDecimal sumTotalFeeByKeyword(@Param("keyword") String keyword);

    @Modifying
    @Query("""
            delete from DianxiaomiPackageFeeRecord r
            where r.totalFee is not null
              and (r.errorMessage is null or r.errorMessage = '')
            """)
    int deleteSuccessRecords();
}
