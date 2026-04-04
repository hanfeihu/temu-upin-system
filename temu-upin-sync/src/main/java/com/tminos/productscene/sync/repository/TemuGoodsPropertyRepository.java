package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuGoodsPropertyRepository extends JpaRepository<TemuGoodsProperty, Long> {

    List<TemuGoodsProperty> findByGoodsId(Long goodsId);

    List<TemuGoodsProperty> findByGoodsIdIn(List<Long> goodsIds);

    void deleteByGoodsId(Long goodsId);
}
