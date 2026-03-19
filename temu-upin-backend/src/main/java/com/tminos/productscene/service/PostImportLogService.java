package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.PostImportLog;
import com.tminos.productscene.entity.PostImportRun;
import com.tminos.productscene.repository.PostImportLogRepository;
import com.tminos.productscene.repository.PostImportRunRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostImportLogService {

    private final PostImportRunRepository runRepo;
    private final PostImportLogRepository logRepo;
    private final ObjectMapper objectMapper;

    public PostImportLogService(PostImportRunRepository runRepo, PostImportLogRepository logRepo, ObjectMapper objectMapper) {
        this.runRepo = runRepo;
        this.logRepo = logRepo;
        this.objectMapper = objectMapper;
    }

    public PostImportRun startRun(Long spuId) {
        PostImportRun r = new PostImportRun();
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
        if (runId == null) return;
        PostImportLog l = new PostImportLog();
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

    public void finishSuccess(Long runId, String summary, String sampleJson) {
        PostImportRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("SUCCEEDED");
        r.setSummary(summary);
        r.setSampleJson(sampleJson);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    public void finishFailed(Long runId, String error, String sampleJson) {
        PostImportRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("FAILED");
        r.setError(error);
        r.setSampleJson(sampleJson);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    public List<PostImportRun> listRuns(Long spuId) {
        if (spuId == null) return runRepo.findTop50ByOrderByIdDesc();
        return runRepo.findBySpuIdOrderByIdDesc(spuId);
    }

    public List<PostImportRun> listRecentRuns() {
        return runRepo.findTop50ByOrderByIdDesc();
    }

    public List<PostImportLog> listLogs(Long runId) {
        return logRepo.findByRunIdOrderByIdAsc(runId);
    }

    public Map<String, Object> summarizeRun(Long runId) {
        PostImportRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("spuId", r.getSpuId());
        out.put("status", r.getStatus());
        out.put("summary", r.getSummary());
        out.put("error", r.getError());
        out.put("startedAt", r.getStartedAt());
        out.put("finishedAt", r.getFinishedAt());
        out.put("logs", listLogs(runId));
        out.put("sampleJson", r.getSampleJson());
        return out;
    }
}
