package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuWarehouseRepository extends JpaRepository<TemuWarehouse, Long> {

    Optional<TemuWarehouse> findByShopIdAndSiteIdAndWarehouseId(String shopId, Integer siteId, String warehouseId);

    List<TemuWarehouse> findByShopId(String shopId);

    List<TemuWarehouse> findByShopIdAndSiteId(String shopId, Integer siteId);
}
