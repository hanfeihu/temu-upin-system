package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuGoodsLifecycleRepository extends JpaRepository<TemuGoodsLifecycle, Long> {

    Optional<TemuGoodsLifecycle> findByShopIdAndSkcId(String shopId, Long skcId);

    List<TemuGoodsLifecycle> findByShopIdAndSkcIdIn(String shopId, List<Long> skcIds);
}
