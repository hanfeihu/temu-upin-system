package com.tminos.productscene.repository;

import com.tminos.productscene.entity.PlatformConfigProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformConfigProfileRepository extends JpaRepository<PlatformConfigProfile, Long> {
    List<PlatformConfigProfile> findAllByOrderByIdAsc();
    Optional<PlatformConfigProfile> findFirstByIsDefaultTrue();
}
