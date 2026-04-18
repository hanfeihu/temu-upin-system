package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuShop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TemuShopRepository extends JpaRepository<TemuShop, Long> {

    List<TemuShop> findByEnabledOrderByIdDesc(Boolean enabled);

    Optional<TemuShop> findByShopId(String shopId);

    List<TemuShop> findByShopIdIn(Collection<String> shopIds);

    boolean existsByShopId(String shopId);

    boolean existsByShopIdAndIdNot(String shopId, Long id);
}
