package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuGoodsSiteRepository extends JpaRepository<TemuGoodsSite, Long> {

    List<TemuGoodsSite> findByGoodsId(Long goodsId);

    List<TemuGoodsSite> findByGoodsIdIn(List<Long> goodsIds);

    void deleteByGoodsId(Long goodsId);
}
