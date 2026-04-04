package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuActivityThematic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuActivityThematicRepository extends JpaRepository<TemuActivityThematic, Long> {

    List<TemuActivityThematic> findByActivityId(Long activityId);

    Optional<TemuActivityThematic> findByActivityIdAndActivityThematicId(Long activityId, Long activityThematicId);
}
