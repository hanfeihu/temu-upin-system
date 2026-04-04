package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuActivity;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuActivityRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ActivitySyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    @Autowired private TemuActivityRepository activityRepo;

    @Override
    protected String getSyncType() { return "ACTIVITY"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        return doSingleDownload(task, client, TemuOpenApiClient.API_ACTIVITY_LIST, "activityList");
    }

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            Integer activityType = toInt(raw.get("activityType"));
            if (activityType == null) continue;

            // Activity 没有唯一约束，按 shopId + activityType + activityName 匹配
            String activityName = toStr(raw.get("activityName"));

            TemuActivity activity = new TemuActivity();
            activity.setShopId(task.getShopId());
            activity.setActivityType(activityType);
            activity.setActivityName(activityName);
            activity.setActivityContent(toStr(raw.get("activityContent")));
            activity.setActivityLabelTag(toInt(raw.get("activityLabelTag")));
            activity.setSessionAssignType(toInt(raw.get("sessionAssignType")));
            activity.setBenefitLabelsJson(toJson(raw.get("benefitLabels")));
            activity.setSyncedAt(LocalDateTime.now());

            activityRepo.save(activity);
        }
    }
}
