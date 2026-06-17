package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface Alibaba1688DetailRecordRepository extends JpaRepository<Alibaba1688DetailRecord, Long>, JpaSpecificationExecutor<Alibaba1688DetailRecord> {

    Optional<Alibaba1688DetailRecord> findByOfferId(String offerId);

    List<Alibaba1688DetailRecord> findAllByOfferIdIn(Collection<String> offerIds);

    @Query(value = """
            select adr.id
            from alibaba_1688_detail_records adr
            where upper(adr.status) = 'READY'
              and (:recordId is null or adr.id = :recordId)
              and (
                    :keyword is null
                    or lower(coalesce(adr.offer_id, '')) like concat('%', :keyword, '%')
                    or lower(coalesce(adr.detail_url, '')) like concat('%', :keyword, '%')
                    or lower(coalesce(adr.canonical_url, '')) like concat('%', :keyword, '%')
                    or lower(coalesce(adr.product_name, '')) like concat('%', :keyword, '%')
                    or lower(coalesce(adr.company_name, '')) like concat('%', :keyword, '%')
              )
              and not exists (
                    select 1
                    from alibaba_1688_selection_pools asp
                    where asp.detail_record_id = adr.id
                       or (adr.offer_id is not null and asp.offer_id = adr.offer_id)
                       or (
                            adr.product_name is not null
                            and asp.product_title_key = lower(regexp_replace(btrim(replace(coalesce(adr.product_name, ''), chr(160), ' ')), '\\s+', ' ', 'g'))
                       )
              )
            order by adr.last_collected_at desc nulls last, adr.id desc
            """, nativeQuery = true)
    List<Long> findReadyUnimportedIds(
            @Param("keyword") String keyword,
            @Param("recordId") Long recordId
    );
}
