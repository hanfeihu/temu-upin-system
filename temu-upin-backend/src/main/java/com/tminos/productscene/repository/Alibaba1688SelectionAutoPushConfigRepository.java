package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionAutoPushConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Alibaba1688SelectionAutoPushConfigRepository extends JpaRepository<Alibaba1688SelectionAutoPushConfig, Long> {

    Optional<Alibaba1688SelectionAutoPushConfig> findTopByOrderByUpdatedAtDescIdDesc();
}
