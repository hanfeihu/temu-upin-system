package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688CardLinkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface Alibaba1688CardLinkRecordRepository extends JpaRepository<Alibaba1688CardLinkRecord, Long>, JpaSpecificationExecutor<Alibaba1688CardLinkRecord> {

    Optional<Alibaba1688CardLinkRecord> findByOfferId(String offerId);

    List<Alibaba1688CardLinkRecord> findAllByOfferIdIn(Collection<String> offerIds);
}
