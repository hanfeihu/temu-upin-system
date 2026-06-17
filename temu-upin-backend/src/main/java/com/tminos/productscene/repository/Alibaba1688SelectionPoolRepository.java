package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Alibaba1688SelectionPoolRepository extends JpaRepository<Alibaba1688SelectionPool, Long>, JpaSpecificationExecutor<Alibaba1688SelectionPool> {

    Optional<Alibaba1688SelectionPool> findByDetailRecordId(Long detailRecordId);

    Optional<Alibaba1688SelectionPool> findByOfferId(String offerId);

    @Query(value = """
            select *
            from alibaba_1688_selection_pools p
            where p.product_title_key = :productTitleKey
               or lower(regexp_replace(btrim(replace(coalesce(p.product_title_snapshot, ''), chr(160), ' ')), '\\s+', ' ', 'g')) = :productTitleKey
            order by p.id asc
            limit 1
            """, nativeQuery = true)
    Optional<Alibaba1688SelectionPool> findDuplicateByProductTitleKey(@Param("productTitleKey") String productTitleKey);

    @Query("""
            select p.categorySnapshot, count(p.id)
            from Alibaba1688SelectionPool p
            where p.categorySnapshot is not null and p.categorySnapshot <> ''
            group by p.categorySnapshot
            order by count(p.id) desc, p.categorySnapshot asc
            """)
    List<Object[]> findDistinctCategories();
}
