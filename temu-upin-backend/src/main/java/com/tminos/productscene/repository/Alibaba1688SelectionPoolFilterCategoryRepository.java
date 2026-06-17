package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionPoolFilterCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface Alibaba1688SelectionPoolFilterCategoryRepository
        extends JpaRepository<Alibaba1688SelectionPoolFilterCategory, Long>,
        JpaSpecificationExecutor<Alibaba1688SelectionPoolFilterCategory> {

    Optional<Alibaba1688SelectionPoolFilterCategory> findByCategoryName(String categoryName);

    boolean existsByCategoryName(String categoryName);

    @Query("""
            select c.categoryName
            from Alibaba1688SelectionPoolFilterCategory c
            where c.enabled = true
              and c.categoryName is not null
              and c.categoryName <> ''
            order by c.updatedAt desc, c.id desc
            """)
    List<String> findEnabledCategoryNames();
}
