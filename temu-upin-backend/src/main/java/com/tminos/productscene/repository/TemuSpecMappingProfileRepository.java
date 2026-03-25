package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuSpecMappingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemuSpecMappingProfileRepository extends JpaRepository<TemuSpecMappingProfile, Long> {
    List<TemuSpecMappingProfile> findByEnabledOrderByIdDesc(Boolean enabled);
    List<TemuSpecMappingProfile> findAllByOrderByIdDesc();
}