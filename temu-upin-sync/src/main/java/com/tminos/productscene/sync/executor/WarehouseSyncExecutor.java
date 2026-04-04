package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.entity.TemuWarehouse;
import com.tminos.productscene.sync.repository.TemuWarehouseRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WarehouseSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    private static final int DEFAULT_SITE_ID_US = 100;

    @Autowired private TemuWarehouseRepository warehouseRepo;

    @Override
    protected String getSyncType() { return "WAREHOUSE"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        TemuOpenApiClient.ApiResult result = client.callApiParsed(
                TemuOpenApiClient.API_WAREHOUSE_LIST,
                buildWarehouseRequest(task));
        if (!result.success) {
            throw new RuntimeException("API 调用失败: " + result.errorMsg);
        }

        Map<String, Object> resultMap = result.resultAsMap();
        if (resultMap == null) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> siteWarehouses = extractList(resultMap, "warehouseDTOList");
        if (siteWarehouses == null || siteWarehouses.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> flattened = new ArrayList<>();
        for (Map<String, Object> siteWarehouse : siteWarehouses) {
            Integer siteId = toInt(siteWarehouse.get("siteId"));
            String siteName = toStr(siteWarehouse.get("siteName"));
            List<Map<String, Object>> validWarehouses = extractList(siteWarehouse, "validWarehouseList");
            if (validWarehouses == null || validWarehouses.isEmpty()) {
                continue;
            }
            for (Map<String, Object> warehouse : validWarehouses) {
                Map<String, Object> row = new HashMap<>(warehouse);
                row.put("siteId", siteId);
                row.put("siteName", siteName);
                flattened.add(row);
            }
        }
        return flattened;
    }

    private Map<String, Object> buildWarehouseRequest(TemuSyncTask task) {
        Long supplierId;
        try {
            supplierId = Long.parseLong(task.getShopId());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("店铺ID不是有效数字，无法作为 supplierId 调用仓库接口: " + task.getShopId(), e);
        }

        Map<String, Object> openApiUser = new HashMap<>();
        openApiUser.put("supplierId", supplierId);

        Map<String, Object> request = new HashMap<>();
        request.put("openApiUser", openApiUser);
        request.put("siteIdList", List.of(DEFAULT_SITE_ID_US));
        return request;
    }

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            String warehouseId = toStr(raw.get("warehouseId"));
            Integer siteId = toInt(raw.get("siteId"));
            if (warehouseId == null || siteId == null) continue;

            TemuWarehouse wh = warehouseRepo.findByShopIdAndSiteIdAndWarehouseId(task.getShopId(), siteId, warehouseId)
                    .orElse(new TemuWarehouse());

            wh.setShopId(task.getShopId());
            wh.setSiteId(siteId);
            wh.setSiteName(toStr(raw.get("siteName")));
            wh.setWarehouseId(warehouseId);
            wh.setWarehouseName(toStr(raw.get("warehouseName")));
            wh.setManagementType(toStr(raw.get("managementType")));
            wh.setWarehouseDisable(toBool(raw.get("warehouseDisable")));
            wh.setSyncedAt(LocalDateTime.now());

            warehouseRepo.save(wh);
        }
    }
}
