package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemuGoodsRepairTransactionalService {

    private static final Logger log = LoggerFactory.getLogger(TemuGoodsRepairTransactionalService.class);

    public record RepairPageResult(int page, int scanned, int repaired, int skipped, boolean success, String errorMessage) {}

    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsAggregateService goodsAggregateService;

    public TemuGoodsRepairTransactionalService(TemuGoodsRepository goodsRepository,
                                               TemuGoodsAggregateService goodsAggregateService) {
        this.goodsRepository = goodsRepository;
        this.goodsAggregateService = goodsAggregateService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairPageResult repairGoodsDetailsPage(String shopId, int page, int pageSize) {
        Page<TemuGoods> goodsPage = goodsRepository.findByShopId(
                shopId,
                PageRequest.of(page, pageSize, Sort.by(Sort.Direction.ASC, "id"))
        );

        int scanned = 0;
        int repaired = 0;
        int skipped = 0;

        for (TemuGoods goods : goodsPage.getContent()) {
            scanned++;
            try {
                boolean success = goodsAggregateService.repairSingleGoodsFromRaw(shopId, goods.getRawJson());
                if (success) {
                    repaired++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                String errorMessage = String.format(
                        "第%d页商品ID=%d, productId=%d, skcId=%d 回填失败: %s",
                        page + 1,
                        goods.getId(),
                        goods.getProductId(),
                        goods.getProductSkcId(),
                        e.getMessage()
                );
                log.error(errorMessage, e);
                throw new IllegalStateException(errorMessage, e);
            }
        }

        return new RepairPageResult(page, scanned, repaired, skipped, true, null);
    }
}