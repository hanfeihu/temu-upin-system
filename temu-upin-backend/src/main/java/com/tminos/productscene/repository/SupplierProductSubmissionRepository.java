package com.tminos.productscene.repository;

import com.tminos.productscene.entity.SupplierProductSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierProductSubmissionRepository extends JpaRepository<SupplierProductSubmission, Long>, JpaSpecificationExecutor<SupplierProductSubmission> {
}
