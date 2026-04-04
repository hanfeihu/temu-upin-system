package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuFreightTemplate;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuFreightTemplateRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class FreightSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    @Autowired private TemuFreightTemplateRepository freightRepo;

    @Override
    protected String getSyncType() { return "FREIGHT"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        return doSingleDownload(task, client, TemuOpenApiClient.API_LOGISTICS_TEMPLATE, "freightTemplateList", "templateList");
    }

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            String templateId = toStr(raw.get("freightTemplateId"));
            if (templateId == null) templateId = toStr(raw.get("templateId"));
            if (templateId == null) continue;

            TemuFreightTemplate tpl = freightRepo.findByShopIdAndFreightTemplateId(task.getShopId(), templateId)
                    .orElse(new TemuFreightTemplate());

            tpl.setShopId(task.getShopId());
            tpl.setFreightTemplateId(templateId);
            tpl.setTemplateName(toStr(raw.get("templateName")));
            tpl.setSyncedAt(LocalDateTime.now());

            freightRepo.save(tpl);
        }
    }
}
