package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuActivityRepository extends JpaRepository<TemuActivity, Long> {

    List<TemuActivity> findByShopId(String shopId);

    List<TemuActivity> findByShopIdAndActivityType(String shopId, Integer activityType);

    Optional<TemuActivity> findFirstByShopIdAndActivityTypeAndActivityName(String shopId, Integer activityType, String activityName);
}
