package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuActivityEnrollPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuActivityEnrollPriceRepository extends JpaRepository<TemuActivityEnrollPrice, Long> {

    List<TemuActivityEnrollPrice> findByEnrollmentId(Long enrollmentId);

    void deleteByEnrollmentId(Long enrollmentId);
}
