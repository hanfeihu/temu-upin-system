package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuActivity;
import com.tminos.productscene.sync.entity.TemuActivityThematic;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuActivityRepository;
import com.tminos.productscene.sync.repository.TemuActivityThematicRepository;
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
    @Autowired private TemuActivityThematicRepository thematicRepo;

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

            TemuActivity activity = activityRepo
                    .findFirstByShopIdAndActivityTypeAndActivityName(task.getShopId(), activityType, activityName)
                    .orElseGet(TemuActivity::new);
            activity.setShopId(task.getShopId());
            activity.setActivityType(activityType);
            activity.setActivityName(activityName);
            activity.setActivityContent(toStr(raw.get("activityContent")));
            activity.setActivityLabelTag(toInt(raw.get("activityLabelTag")));
            activity.setSessionAssignType(toInt(raw.get("sessionAssignType")));
            activity.setBenefitLabelsJson(toJson(raw.get("benefitLabels")));
            activity.setSyncedAt(LocalDateTime.now());

            activity = activityRepo.save(activity);
            persistThematics(activity.getId(), raw.get("thematicList"));
        }
    }

    private void persistThematics(Long activityId, Object thematicListObject) {
        if (activityId == null || !(thematicListObject instanceof List<?> thematicList)) {
            return;
        }
        for (Object item : thematicList) {
            Map<String, Object> raw = toMap(item);
            if (raw == null) {
                continue;
            }
            Long thematicId = toLong(raw.get("activityThematicId"));
            if (thematicId == null) {
                continue;
            }
            TemuActivityThematic thematic = thematicRepo
                    .findByActivityIdAndActivityThematicId(activityId, thematicId)
                    .orElseGet(TemuActivityThematic::new);
            thematic.setActivityId(activityId);
            thematic.setActivityThematicId(thematicId);
            thematic.setActivityThematicName(toStr(raw.get("activityThematicName")));
            thematic.setEnrollSource(toInt(raw.get("enrollSource")));
            thematic.setEnrollStartAt(toLong(raw.get("enrollStartAt")));
            thematic.setEnrollDeadLine(toLong(raw.get("enrollDeadLine")));
            thematic.setStartTime(toLong(raw.get("startTime")));
            thematic.setEndTime(toLong(raw.get("endTime")));
            thematic.setDurationDays(toInt(raw.get("durationDays")));
            thematic.setSalePromotionLabel(toStr(raw.get("salePromotionLabel")));
            thematic.setActivityLabelTag(toInt(raw.get("activityLabelTag")));
            thematic.setBenefitLabelsJson(toJson(raw.get("benefitLabels")));
            thematic.setSitesJson(toJson(raw.get("sites")));
            thematicRepo.save(thematic);
        }
    }
}
