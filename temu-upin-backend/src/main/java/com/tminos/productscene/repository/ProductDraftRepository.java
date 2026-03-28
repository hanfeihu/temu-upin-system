package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ProductDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductDraftRepository extends JpaRepository<ProductDraft, Long>, JpaSpecificationExecutor<ProductDraft> {
}
