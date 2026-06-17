package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuActivityBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemuActivityBlacklistRepository extends JpaRepository<TemuActivityBlacklist, Long> {

    Optional<TemuActivityBlacklist> findByShopIdAndProductId(String shopId, Long productId);

    List<TemuActivityBlacklist> findByShopIdAndProductIdIn(String shopId, Collection<Long> productIds);
}
