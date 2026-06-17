package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuSitePublishException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemuSitePublishExceptionRepository extends JpaRepository<TemuSitePublishException, Long>, JpaSpecificationExecutor<TemuSitePublishException> {

    Optional<TemuSitePublishException> findFirstBySkcAndSiteName(String skc, String siteName);

    boolean existsByOfferIdAndActiveTrue(String offerId);

    List<TemuSitePublishException> findAllByOfferIdInAndActiveTrue(Collection<String> offerIds);

    @Query("""
            select distinct e.offerId
            from TemuSitePublishException e
            where e.active = true
              and e.offerId in :offerIds
              and e.offerId is not null
              and e.offerId <> ''
            """)
    List<String> findActiveOfferIds(@Param("offerIds") Collection<String> offerIds);
}
