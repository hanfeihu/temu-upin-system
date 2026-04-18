package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ProductCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ProductCollectionRepository extends JpaRepository<ProductCollection, Long>, JpaSpecificationExecutor<ProductCollection> {
    Page<ProductCollection> findByDeletedFalse(Pageable pageable);

    @Query("select p from ProductCollection p where (p.execStatus = 0 or p.execStatus is null) order by p.id asc")
    List<ProductCollection> findPendingExecTasks(Pageable pageable);

    @Query("select p from ProductCollection p where (p.execStatus = 0 or p.execStatus is null) and (p.ocrStatus = 2) order by p.id asc")
    List<ProductCollection> findPendingExecTasksWithOcrDone(Pageable pageable);

    /**
     * Try to claim a pending task. Returns 1 if claimed by this instance.
     */
    @Modifying
    @Query("update ProductCollection p set p.execStatus=1, p.execResult=:result, p.updatedAt=CURRENT_TIMESTAMP where p.id=:id and (p.execStatus=0 or p.execStatus is null)")
    int claimExecTask(@Param("id") Long id, @Param("result") String result);

    @Query("select distinct p.temuCatid, p.temuCatname from ProductCollection p " +
            "where (:showDeleted = true or p.deleted = false) " +
            "and p.temuCatid is not null and p.temuCatname is not null " +
            "order by p.temuCatname asc")
    List<Object[]> findDistinctTemuCategories(@Param("showDeleted") boolean showDeleted);

    @Query(
            value = """
                    select
                      x.id,
                      x.product_id,
                      x.product_name,
                      x.source_platform,
                      x.collection_status,
                      x.exec_status,
                      x.exec_result,
                      x.last_publish_run_id,
                      x.collect_count,
                      x.company_name,
                      x.target_shop_ids,
                      x.target_shop_names,
                      x.product_main_image,
                      x.temu_catid,
                      x.temu_catname,
                      x.temu_published,
                      x.temu_goods_id,
                      x.min_price,
                      x.max_price,
                      x.ocr_status,
                      x.carousel_image_count,
                      x.detail_image_count,
                      x.sku_count,
                      x.moq,
                      x.moq_text,
                      x.net_weight,
                      x.packaging_weight,
                      x.deleted,
                      x.created_at,
                      x.updated_at
                    from (
                      select
                        pc.*,
                        jsonb_array_length(
                          case
                            when pc.carousel_images is null or btrim(pc.carousel_images) = '' then cast('[]' as jsonb)
                            when left(btrim(pc.carousel_images), 1) = '[' then cast(pc.carousel_images as jsonb)
                            else cast('[]' as jsonb)
                          end
                        ) as carousel_image_count,
                        jsonb_array_length(
                          case
                            when pc.detail_images is null or btrim(pc.detail_images) = '' then cast('[]' as jsonb)
                            when left(btrim(pc.detail_images), 1) = '[' then cast(pc.detail_images as jsonb)
                            else cast('[]' as jsonb)
                          end
                        ) as detail_image_count,
                        coalesce(nullif(ts.temu_sku_count, 0), os.origin_sku_count, 0) as sku_count
                      from product_collection pc
                      left join (
                        select spu_id, count(*) as origin_sku_count
                        from product_collection_sku
                        group by spu_id
                      ) os on os.spu_id = pc.id
                      left join (
                        select spu_id, count(*) as temu_sku_count
                        from product_collection_temu_sku
                        group by spu_id
                      ) ts on ts.spu_id = pc.id
                      where
                        (:showDeleted = true or pc.deleted = false)
                        and (:sourcePlatform is null or pc.source_platform = :sourcePlatform)
                        and (
                          :targetShopId is null
                          or exists (
                            select 1
                            from jsonb_array_elements_text(
                              case
                                when pc.target_shop_ids is null or btrim(pc.target_shop_ids) = '' then cast('[]' as jsonb)
                                when left(btrim(pc.target_shop_ids), 1) = '[' then cast(pc.target_shop_ids as jsonb)
                                else cast('[]' as jsonb)
                              end
                            ) as shop_id(value)
                            where shop_id.value = :targetShopId
                          )
                        )
                        and (
                          :collectionStatus is null
                          or (
                            :collectionStatus = 0
                            and coalesce(pc.collection_status, 0) = 0
                            and coalesce(pc.temu_published, false) = false
                          )
                          or (
                            :collectionStatus <> 0
                            and :collectionStatus <> 3
                            and pc.collection_status = :collectionStatus
                          )
                          or (
                            :collectionStatus = 3
                            and coalesce(pc.temu_published, false) = true
                          )
                        )
                        and (:temuCatid is null or pc.temu_catid = :temuCatid)
                        and (:moqMin is null or pc.moq >= :moqMin)
                        and (:moqMax is null or pc.moq <= :moqMax)
                        and (
                          :q is null
                          or pc.product_name ilike concat('%', :q, '%')
                          or pc.product_id ilike concat('%', :q, '%')
                          or pc.company_name ilike concat('%', :q, '%')
                        )
                    ) x
                    where
                      (:carouselImageCountMin is null or x.carousel_image_count >= :carouselImageCountMin)
                      and (:carouselImageCountMax is null or x.carousel_image_count <= :carouselImageCountMax)
                      and (:detailImageCountMin is null or x.detail_image_count >= :detailImageCountMin)
                      and (:detailImageCountMax is null or x.detail_image_count <= :detailImageCountMax)
                      and (:skuCountMin is null or x.sku_count >= :skuCountMin)
                      and (:skuCountMax is null or x.sku_count <= :skuCountMax)
                    order by x.updated_at desc
                    """,
            countQuery = """
                    select count(*)
                    from (
                      select
                        pc.id,
                        jsonb_array_length(
                          case
                            when pc.carousel_images is null or btrim(pc.carousel_images) = '' then cast('[]' as jsonb)
                            when left(btrim(pc.carousel_images), 1) = '[' then cast(pc.carousel_images as jsonb)
                            else cast('[]' as jsonb)
                          end
                        ) as carousel_image_count,
                        jsonb_array_length(
                          case
                            when pc.detail_images is null or btrim(pc.detail_images) = '' then cast('[]' as jsonb)
                            when left(btrim(pc.detail_images), 1) = '[' then cast(pc.detail_images as jsonb)
                            else cast('[]' as jsonb)
                          end
                        ) as detail_image_count,
                        coalesce(nullif(ts.temu_sku_count, 0), os.origin_sku_count, 0) as sku_count
                      from product_collection pc
                      left join (
                        select spu_id, count(*) as origin_sku_count
                        from product_collection_sku
                        group by spu_id
                      ) os on os.spu_id = pc.id
                      left join (
                        select spu_id, count(*) as temu_sku_count
                        from product_collection_temu_sku
                        group by spu_id
                      ) ts on ts.spu_id = pc.id
                      where
                        (:showDeleted = true or pc.deleted = false)
                        and (:sourcePlatform is null or pc.source_platform = :sourcePlatform)
                        and (
                          :targetShopId is null
                          or exists (
                            select 1
                            from jsonb_array_elements_text(
                              case
                                when pc.target_shop_ids is null or btrim(pc.target_shop_ids) = '' then cast('[]' as jsonb)
                                when left(btrim(pc.target_shop_ids), 1) = '[' then cast(pc.target_shop_ids as jsonb)
                                else cast('[]' as jsonb)
                              end
                            ) as shop_id(value)
                            where shop_id.value = :targetShopId
                          )
                        )
                        and (
                          :collectionStatus is null
                          or (
                            :collectionStatus = 0
                            and coalesce(pc.collection_status, 0) = 0
                            and coalesce(pc.temu_published, false) = false
                          )
                          or (
                            :collectionStatus <> 0
                            and :collectionStatus <> 3
                            and pc.collection_status = :collectionStatus
                          )
                          or (
                            :collectionStatus = 3
                            and coalesce(pc.temu_published, false) = true
                          )
                        )
                        and (:temuCatid is null or pc.temu_catid = :temuCatid)
                        and (:moqMin is null or pc.moq >= :moqMin)
                        and (:moqMax is null or pc.moq <= :moqMax)
                        and (
                          :q is null
                          or pc.product_name ilike concat('%', :q, '%')
                          or pc.product_id ilike concat('%', :q, '%')
                          or pc.company_name ilike concat('%', :q, '%')
                        )
                    ) x
                    where
                      (:carouselImageCountMin is null or x.carousel_image_count >= :carouselImageCountMin)
                      and (:carouselImageCountMax is null or x.carousel_image_count <= :carouselImageCountMax)
                      and (:detailImageCountMin is null or x.detail_image_count >= :detailImageCountMin)
                      and (:detailImageCountMax is null or x.detail_image_count <= :detailImageCountMax)
                      and (:skuCountMin is null or x.sku_count >= :skuCountMin)
                      and (:skuCountMax is null or x.sku_count <= :skuCountMax)
                    """,
            nativeQuery = true
    )
    Page<Object[]> searchWithCounts(
            @Param("q") String q,
            @Param("sourcePlatform") String sourcePlatform,
            @Param("targetShopId") String targetShopId,
            @Param("collectionStatus") Integer collectionStatus,
            @Param("showDeleted") boolean showDeleted,
            @Param("temuCatid") String temuCatid,
            @Param("moqMin") Integer moqMin,
            @Param("moqMax") Integer moqMax,
            @Param("carouselImageCountMin") Integer carouselImageCountMin,
            @Param("carouselImageCountMax") Integer carouselImageCountMax,
            @Param("detailImageCountMin") Integer detailImageCountMin,
            @Param("detailImageCountMax") Integer detailImageCountMax,
            @Param("skuCountMin") Integer skuCountMin,
            @Param("skuCountMax") Integer skuCountMax,
            Pageable pageable
    );
}
