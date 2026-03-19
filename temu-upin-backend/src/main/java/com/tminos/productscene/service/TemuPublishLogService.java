package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.TemuPublishLog;
import com.tminos.productscene.entity.TemuPublishRun;
import com.tminos.productscene.repository.TemuPublishLogRepository;
import com.tminos.productscene.repository.TemuPublishRunRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuPublishLogService {

    private final TemuPublishRunRepository runRepo;
    private final TemuPublishLogRepository logRepo;
    private final ObjectMapper objectMapper;

    public TemuPublishLogService(TemuPublishRunRepository runRepo, TemuPublishLogRepository logRepo, ObjectMapper objectMapper) {
        this.runRepo = runRepo;
        this.logRepo = logRepo;
        this.objectMapper = objectMapper;
    }

    public TemuPublishRun startRun(Long spuId) {
        TemuPublishRun r = new TemuPublishRun();
        r.setSpuId(spuId);
        r.setStatus("STARTED");
        return runRepo.save(r);
    }

    public void info(Long runId, String stage, String message) {
        save(runId, stage, "INFO", message, null);
    }

    public void warn(Long runId, String stage, String message) {
        save(runId, stage, "WARN", message, null);
    }

    public void error(Long runId, String stage, String message, Object data) {
        save(runId, stage, "ERROR", message, data);
    }

    public void data(Long runId, String stage, String message, Object data) {
        save(runId, stage, "INFO", message, data);
    }

    private void save(Long runId, String stage, String level, String message, Object data) {
        TemuPublishLog l = new TemuPublishLog();
        l.setRunId(runId);
        l.setStage(stage == null ? "" : stage);
        l.setLevel(level);
        l.setMessage(message);
        if (data != null) {
            try {
                l.setDataJson(objectMapper.writeValueAsString(data));
            } catch (Exception ignored) {
                l.setDataJson(String.valueOf(data));
            }
        }
        logRepo.save(l);
    }

    public void finishSuccess(Long runId, String goodsId, String requestJson, String responseRaw) {
        TemuPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("SUCCEEDED");
        r.setGoodsId(goodsId);
        r.setRequestJson(requestJson);
        r.setResponseRaw(responseRaw);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    public void finishFailed(Long runId, String error, String requestJson, String responseRaw) {
        TemuPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("FAILED");
        r.setError(error);
        r.setRequestJson(requestJson);
        r.setResponseRaw(responseRaw);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    public List<TemuPublishRun> listRuns(Long spuId) {
        if (spuId == null) {
            return runRepo.findTop50ByOrderByIdDesc();
        }
        return runRepo.findBySpuIdOrderByIdDesc(spuId);
    }

    public List<TemuPublishRun> listRecentRuns() {
        return runRepo.findTop50ByOrderByIdDesc();
    }

    public List<TemuPublishLog> listLogs(Long runId) {
        return logRepo.findByRunIdOrderByIdAsc(runId);
    }

    public Map<String, Object> summarizeRun(Long runId) {
        TemuPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("spuId", r.getSpuId());
        out.put("status", r.getStatus());
        out.put("goodsId", r.getGoodsId());
        out.put("startedAt", r.getStartedAt());
        out.put("finishedAt", r.getFinishedAt());
        out.put("error", r.getError());
        out.put("logs", listLogs(runId));
        return out;
    }
}
