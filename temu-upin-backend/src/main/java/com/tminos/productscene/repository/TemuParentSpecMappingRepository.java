package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuParentSpecMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemuParentSpecMappingRepository extends JpaRepository<TemuParentSpecMapping, Long> {
    List<TemuParentSpecMapping> findAllByOrderByIdDesc();
    List<TemuParentSpecMapping> findByEnabledOrderByIdDesc(Boolean enabled);
    List<TemuParentSpecMapping> findByEnabledTrueOrderByIdAsc();
    Optional<TemuParentSpecMapping> findByNormalizedSourceField(String normalizedSourceField);
    boolean existsByNormalizedSourceFieldAndIdNot(String normalizedSourceField, Long id);
    boolean existsByNormalizedSourceField(String normalizedSourceField);
}