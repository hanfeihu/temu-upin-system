package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionPoolReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface Alibaba1688SelectionPoolReportRepository extends JpaRepository<Alibaba1688SelectionPoolReport, Long> {

    List<Alibaba1688SelectionPoolReport> findAllByPoolIdOrderByCreatedAtDescIdDesc(Long poolId);

    List<Alibaba1688SelectionPoolReport> findAllByPoolIdInOrderByCreatedAtDescIdDesc(Collection<Long> poolIds);

    long countByPoolIdAndReportType(Long poolId, String reportType);
}
