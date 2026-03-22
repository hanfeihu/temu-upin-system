package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuShop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemuShopRepository extends JpaRepository<TemuShop, Long> {

    List<TemuShop> findByEnabledOrderByIdDesc(Boolean enabled);

    boolean existsByShopId(String shopId);

    boolean existsByShopIdAndIdNot(String shopId, Long id);
}
