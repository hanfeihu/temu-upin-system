package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuActivitySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuActivitySessionRepository extends JpaRepository<TemuActivitySession, Long> {

    Optional<TemuActivitySession> findByShopIdAndSessionId(String shopId, Long sessionId);

    List<TemuActivitySession> findByShopIdAndActivityType(String shopId, Integer activityType);

    List<TemuActivitySession> findByShopIdAndActivityTypeAndSessionStatus(String shopId, Integer activityType, Integer sessionStatus);
}
