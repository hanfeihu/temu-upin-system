package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionAutoPushLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Alibaba1688SelectionAutoPushLogRepository extends JpaRepository<Alibaba1688SelectionAutoPushLog, Long> {

    Page<Alibaba1688SelectionAutoPushLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
