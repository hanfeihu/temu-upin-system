package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsDecoration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuGoodsDecorationRepository extends JpaRepository<TemuGoodsDecoration, Long> {

    List<TemuGoodsDecoration> findByGoodsId(Long goodsId);

    void deleteByGoodsId(Long goodsId);
}
