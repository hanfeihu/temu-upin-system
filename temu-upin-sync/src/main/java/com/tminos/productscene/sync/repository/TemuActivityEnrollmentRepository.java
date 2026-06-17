package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuActivityEnrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuActivityEnrollmentRepository extends JpaRepository<TemuActivityEnrollment, Long> {

    Optional<TemuActivityEnrollment> findByShopIdAndEnrollId(String shopId, Long enrollId);

    Page<TemuActivityEnrollment> findByShopId(String shopId, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdIn(String shopId, List<Long> productIds, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdInAndActivityType(String shopId, List<Long> productIds, Integer activityType, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdInAndEnrollStatus(String shopId, List<Long> productIds, Integer enrollStatus, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdInAndActivityTypeAndEnrollStatus(String shopId, List<Long> productIds, Integer activityType, Integer enrollStatus, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductId(String shopId, Long productId, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdAndActivityType(String shopId, Long productId, Integer activityType, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdAndEnrollStatus(String shopId, Long productId, Integer enrollStatus, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndProductIdAndActivityTypeAndEnrollStatus(String shopId, Long productId, Integer activityType, Integer enrollStatus, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndActivityType(String shopId, Integer activityType, Pageable pageable);

    Page<TemuActivityEnrollment> findByShopIdAndEnrollStatus(String shopId, Integer enrollStatus, Pageable pageable);

    List<TemuActivityEnrollment> findByShopIdAndProductIdIn(String shopId, List<Long> productIds);
}
