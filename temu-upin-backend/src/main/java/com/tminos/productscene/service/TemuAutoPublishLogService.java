package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.TemuAutoPublishLog;
import com.tminos.productscene.entity.TemuAutoPublishRun;
import com.tminos.productscene.repository.TemuAutoPublishLogRepository;
import com.tminos.productscene.repository.TemuAutoPublishRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuAutoPublishLogService {

    private final TemuAutoPublishRunRepository runRepo;
    private final TemuAutoPublishLogRepository logRepo;
    private final ObjectMapper objectMapper;

    public TemuAutoPublishLogService(TemuAutoPublishRunRepository runRepo,
                                    TemuAutoPublishLogRepository logRepo,
                                    ObjectMapper objectMapper) {
        this.runRepo = runRepo;
        this.logRepo = logRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TemuAutoPublishRun startRun(Long spuId) {
        TemuAutoPublishRun r = new TemuAutoPublishRun();
        r.setSpuId(spuId);
        r.setStatus("STARTED");
        r.setAction("SKIP");
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
        TemuAutoPublishLog l = new TemuAutoPublishLog();
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

    @Transactional
    public void finishSkipped(Long runId, String eligibilityJson, String summary) {
        if (runId == null) return;
        TemuAutoPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("SKIPPED");
        r.setAction("SKIP");
        r.setEligibilityJson(eligibilityJson);
        r.setSummary(summary);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    @Transactional
    public void finishSucceeded(Long runId, Long publishRunId, String eligibilityJson, String summary) {
        if (runId == null) return;
        TemuAutoPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("SUCCEEDED");
        r.setAction("PUBLISH");
        r.setPublishRunId(publishRunId);
        r.setEligibilityJson(eligibilityJson);
        r.setSummary(summary);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    @Transactional
    public void finishFailed(Long runId, Long publishRunId, String eligibilityJson, String error) {
        if (runId == null) return;
        TemuAutoPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return;
        r.setStatus("FAILED");
        r.setAction("PUBLISH");
        r.setPublishRunId(publishRunId);
        r.setEligibilityJson(eligibilityJson);
        r.setError(error);
        r.setFinishedAt(LocalDateTime.now());
        runRepo.save(r);
    }

    @Transactional(readOnly = true)
    public List<TemuAutoPublishRun> listRecentRuns() {
        return runRepo.findTop50ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<TemuAutoPublishRun> listRuns(Long spuId) {
        if (spuId == null) return listRecentRuns();
        return runRepo.findBySpuIdOrderByIdDesc(spuId);
    }

    @Transactional(readOnly = true)
    public Page<TemuAutoPublishRun> searchRuns(Long spuId,
                                              String status,
                                              String action,
                                              String q,
                                              int page,
                                              int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));

        Specification<TemuAutoPublishRun> spec = (root, query0, cb) -> {
            var p = cb.conjunction();
            if (spuId != null) {
                p = cb.and(p, cb.equal(root.get("spuId"), spuId));
            }
            if (StringUtils.hasText(status)) {
                p = cb.and(p, cb.equal(root.get("status"), status.trim()));
            }
            if (StringUtils.hasText(action)) {
                p = cb.and(p, cb.equal(root.get("action"), action.trim()));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim() + "%";
                p = cb.and(p, cb.or(
                        cb.like(root.get("summary"), like),
                        cb.like(root.get("error"), like),
                        cb.like(root.get("eligibilityJson"), like)
                ));
            }
            return p;
        };

        return runRepo.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<TemuAutoPublishLog> listLogs(Long runId) {
        return logRepo.findByRunIdOrderByIdAsc(runId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summarizeRun(Long runId) {
        if (runId == null) return null;
        TemuAutoPublishRun r = runRepo.findById(runId).orElse(null);
        if (r == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("spuId", r.getSpuId());
        out.put("status", r.getStatus());
        out.put("action", r.getAction());
        out.put("publishRunId", r.getPublishRunId());
        out.put("startedAt", r.getStartedAt());
        out.put("finishedAt", r.getFinishedAt());
        out.put("summary", r.getSummary());
        out.put("error", r.getError());
        out.put("eligibilityJson", r.getEligibilityJson());
        out.put("logs", listLogs(runId));
        return out;
    }

    @Transactional
    public Map<String, Object> clearAllLogs() {
        long runCount = runRepo.count();
        long logCount = logRepo.count();

        if (logCount > 0) {
            logRepo.deleteAllInBatch();
        }
        if (runCount > 0) {
            runRepo.deleteAllInBatch();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deletedRuns", runCount);
        result.put("deletedLogs", logCount);
        result.put("message", runCount == 0 && logCount == 0
                ? "当前没有可清空的自动发布日志数据"
                : String.format("已清空 TEMU 自动发布日志：%d 条 run，%d 条明细日志", runCount, logCount));
        return result;
    }

    public String toJsonSafe(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    public String buildEligibilitySummary(TemuAutoPublishEligibilityService.EligibilityResult r) {
        if (r == null) return null;
        List<String> parts = new ArrayList<>();
        if (r.eligible()) {
            parts.add("eligible=true");
        } else {
            parts.add("eligible=false");
        }
        if (r.reasons() != null && !r.reasons().isEmpty()) {
            parts.add("reasons=" + String.join("; ", r.reasons()));
        }
        return String.join(" | ", parts);
    }

    public String buildEligibilityDisplaySummary(TemuAutoPublishEligibilityService.EligibilityResult r) {
        if (r == null) return null;
        if (r.checks() == null || r.checks().isEmpty()) {
            return buildEligibilitySummary(r);
        }
        List<String> failed = new ArrayList<>();
        for (TemuAutoPublishEligibilityService.RuleCheck c : r.checks()) {
            if (c == null) continue;
            if (c.passed()) continue;
            Object d = c.details() == null ? null : c.details().get("display");
            String s = d == null ? null : String.valueOf(d);
            if (s != null && !s.isBlank()) {
                failed.add("[" + c.ruleId() + "] " + s);
            } else {
                failed.add("[" + c.ruleId() + "] " + c.ruleName());
            }
        }
        if (failed.isEmpty()) {
            return "eligible=true";
        }
        // Keep it short for list view; full details are available in eligibilityJson.
        int limit = Math.min(failed.size(), 4);
        String head = String.join("; ", failed.subList(0, limit));
        if (failed.size() > limit) {
            head = head + " ... (+" + (failed.size() - limit) + ")";
        }
        return "eligible=false | " + head;
    }
}
