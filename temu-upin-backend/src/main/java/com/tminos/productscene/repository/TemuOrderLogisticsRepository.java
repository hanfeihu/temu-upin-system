package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuOrderLogistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TemuOrderLogisticsRepository extends JpaRepository<TemuOrderLogistics, Long> {

    Optional<TemuOrderLogistics> findByShopRecordIdAndParentOrderSnAndProviderCode(Long shopRecordId, String parentOrderSn, String providerCode);

    List<TemuOrderLogistics> findByShopRecordIdAndParentOrderSnInAndProviderCode(Long shopRecordId, Collection<String> parentOrderSns, String providerCode);

    List<TemuOrderLogistics> findByShopRecordIdAndParentOrderSnIn(Long shopRecordId, Collection<String> parentOrderSns);

    Optional<TemuOrderLogistics> findFirstByShopRecordIdAndParentOrderSnOrderByUpdatedAtDesc(Long shopRecordId, String parentOrderSn);
}
