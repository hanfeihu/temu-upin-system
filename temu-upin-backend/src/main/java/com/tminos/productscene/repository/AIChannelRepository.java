package com.tminos.productscene.repository;

import com.tminos.productscene.entity.AIChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AIChannelRepository extends JpaRepository<AIChannel, Long> {
    List<AIChannel> findByEnabledTrueOrderBySortOrderAsc();
    List<AIChannel> findByPlatformOrderBySortOrderAsc(String platform);
}
