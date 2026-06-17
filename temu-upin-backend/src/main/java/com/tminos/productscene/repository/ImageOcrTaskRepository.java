package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ImageOcrTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageOcrTaskRepository extends JpaRepository<ImageOcrTask, Long>, JpaSpecificationExecutor<ImageOcrTask> {

    Page<ImageOcrTask> findBySpuId(Long spuId, Pageable pageable);

    List<ImageOcrTask> findBySpuId(Long spuId);

    @Query("select count(t) from ImageOcrTask t where t.spuId = :spuId and t.execStatus <> 2")
    long countNotSuccessBySpuId(@Param("spuId") Long spuId);

    @Query("select count(t) from ImageOcrTask t where t.spuId = :spuId")
    long countBySpuId(@Param("spuId") Long spuId);

    @Query("select t from ImageOcrTask t where t.imageUrl is not null and (t.imageWidth is null or t.imageHeight is null or t.imageMd5 is null) order by t.id asc")
    List<ImageOcrTask> findMissingImageMetadata(Pageable pageable);

    @Query("select count(t) from ImageOcrTask t where t.imageUrl is not null and (t.imageWidth is null or t.imageHeight is null or t.imageMd5 is null)")
    long countMissingImageMetadata();

    @Query("""
            select t from ImageOcrTask t
            where exists (
                select c from ImageOcrSizeFilterConfig c
                where c.enabled = true
                  and c.imageWidth = t.imageWidth
                  and c.imageHeight = t.imageHeight
            )
            order by t.spuId asc, t.sourceField asc, t.sourceIndex asc, t.id asc
            """)
    List<ImageOcrTask> findEnabledSizeFilteredTasks();

    @Query("select t from ImageOcrTask t where t.execStatus = 0 order by t.id asc")
    List<ImageOcrTask> findPending(Pageable pageable);

    /**
     * Claim next pending task with SKIP LOCKED. Returns claimed id or null.
     */
    @Query(value = "select id from image_ocr_task where exec_status = 0 order by id asc for update skip locked limit 1", nativeQuery = true)
    List<Number> claimNextPendingIdNative();

    @Modifying
    @Query(value = "update image_ocr_task set exec_status = 1, executor_ip = :ip, task_started_at = now(), updated_at = now() where id = :id and exec_status = 0", nativeQuery = true)
    int markRunning(@Param("id") Long id, @Param("ip") String ip);
}
