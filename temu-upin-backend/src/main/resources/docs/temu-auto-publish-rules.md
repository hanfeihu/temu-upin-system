# TEMU Auto Publish Rules

This document defines the eligibility rules for **automatic TEMU publishing**.

Purpose:
- The UI publishes a single product manually by clicking the publish button on the `product-collections` page.
- The backend worker publishes automatically when a product meets all rules below.

Notes:
- When rules change, update this document first; code should follow the document.
- The worker is best-effort: it skips non-eligible products and continues.

## Eligibility Rules (all must pass)

0. Product status is 未发布
   - `product_collection.collection_status is null or == 0`
   - Note: auto worker will only claim candidates from 未发布 state (it will set status to 发布中 while processing)

1. OCR status is completed
   - `product_collection.ocr_status == 2`

2. Execution status is completed
   - `product_collection.exec_status == 2`

3. Carousel image count is not more than 10
   - `carousel_images` JSON array length `<= 10`

4. TEMU category id is not empty
   - `product_collection.temu_catid` is not null/blank

5. TEMU attributes is not empty
   - `product_collection.temu_attributes` is not null/blank

6. OCR text does NOT contain any Chinese characters (only check images that were not filtered)
   - For `image_ocr_task` rows where `spu_id = product_collection.id` and `filtered = false`:
     - If `exec_result` contains any Chinese character (CJK), the product is NOT eligible
   - Only validate `filtered = false` tasks because when `filtered = true` the image will be removed.

7. SKU count is less than 10
   - Origin SKU count: `count(product_collection_sku where spu_id = product_collection.id) < 10`

8. TEMU SKU count is more than 0
   - TEMU SKU count: `count(product_collection_temu_sku where spu_id = product_collection.id) > 0`

## Configuration

The worker is disabled by default.

- Enable: `temu.auto-publish.worker.enabled` (env: `TEMU_AUTO_PUBLISH_ENABLED`)
- Poll interval: `temu.auto-publish.worker.poll-ms` (env: `TEMU_AUTO_PUBLISH_POLL_MS`)
