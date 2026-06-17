package com.tminos.productscene.repository;

import com.tminos.productscene.entity.SupplierProductPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierProductPackageRepository extends JpaRepository<SupplierProductPackage, Long>, JpaSpecificationExecutor<SupplierProductPackage> {
    Optional<SupplierProductPackage> findBySubmissionId(Long submissionId);
    void deleteBySubmissionId(Long submissionId);
}
