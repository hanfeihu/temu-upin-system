package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.Alibaba1688DetailTaskDTO;
import com.tminos.productscene.entity.Alibaba1688AuthSession;
import com.tminos.productscene.entity.Alibaba1688CardLinkRecord;
import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import com.tminos.productscene.entity.Alibaba1688DetailTask;
import com.tminos.productscene.repository.Alibaba1688AuthSessionRepository;
import com.tminos.productscene.repository.Alibaba1688CardLinkRecordRepository;
import com.tminos.productscene.repository.Alibaba1688DetailRecordRepository;
import com.tminos.productscene.repository.Alibaba1688DetailTaskRepository;
import com.tminos.productscene.service.pull.parser.Alibaba1688HtmlParser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class Alibaba1688DetailTaskService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFER_ID_IN_URL_PATTERN = Pattern.compile("(?:offerId=|/offer/)(\\d{8,20})", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of("PENDING", "RUNNING");

    private final Alibaba1688DetailTaskRepository taskRepository;
    private final Alibaba1688DetailRecordRepository recordRepository;
    private final Alibaba1688AuthSessionRepository authSessionRepository;
    private final Alibaba1688CardLinkRecordRepository cardLinkRecordRepository;
    private final ObjectMapper objectMapper;

    public Alibaba1688DetailTaskService(
            Alibaba1688DetailTaskRepository taskRepository,
            Alibaba1688DetailRecordRepository recordRepository,
            Alibaba1688AuthSessionRepository authSessionRepository,
            Alibaba1688CardLinkRecordRepository cardLinkRecordRepository,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.recordRepository = recordRepository;
        this.authSessionRepository = authSessionRepository;
        this.cardLinkRecordRepository = cardLinkRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<Alibaba1688DetailTaskDTO.ListItem> list(String keyword, Long credentialId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")
        );
        Specification<Alibaba1688DetailTask> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (credentialId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("credentialId"), credentialId));
            }
            if (StringUtils.hasText(status)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase()));
            }
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("offerId")), likeValue),
                        cb.like(cb.lower(root.get("detailUrl")), likeValue),
                        cb.like(cb.lower(root.get("workerName")), likeValue),
                        cb.like(cb.lower(root.get("lastError")), likeValue)
                ));
            }
            return predicate;
        };

        Page<Alibaba1688DetailTask> data = taskRepository.findAll(spec, pageable);
        Map<Long, String> credentialNameMap = loadCredentialNameMap(
                data.stream().map(Alibaba1688DetailTask::getCredentialId).collect(Collectors.toSet())
        );
        return data.map(item -> toListItem(item, credentialNameMap.get(item.getCredentialId())));
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.CreateDialogResponse createFromDialog(Alibaba1688DetailTaskDTO.CreateDialogRequest request) {
        Alibaba1688AuthSession credential = resolveCredential(request == null ? null : request.getCredentialId());

        List<ParsedTaskCandidate> candidates = parseTaskCandidates(request == null ? null : request.getRawInput());
        boolean forceRefresh = Boolean.TRUE.equals(request == null ? null : request.getForceRefresh());
        Map<String, ParsedTaskCandidate> deduped = new LinkedHashMap<>();
        int invalidCount = 0;
        for (ParsedTaskCandidate candidate : candidates) {
            if (!StringUtils.hasText(candidate.offerId())) {
                invalidCount++;
                continue;
            }
            deduped.putIfAbsent(candidate.offerId(), candidate);
        }

        if (deduped.isEmpty()) {
            return Alibaba1688DetailTaskDTO.CreateDialogResponse.builder()
                    .parsedUrlCount(0)
                    .createdCount(0)
                    .skippedExistingRecordCount(0)
                    .skippedActiveTaskCount(0)
                    .invalidCount(invalidCount)
                    .createdItems(List.of())
                    .build();
        }

        CreateTaskSummary summary = createTasks(
                credential,
                deduped.values(),
                forceRefresh,
                "MANUAL_DIALOG"
        );

        return Alibaba1688DetailTaskDTO.CreateDialogResponse.builder()
                .parsedUrlCount(deduped.size())
                .createdCount(summary.createdCount())
                .skippedExistingRecordCount(summary.skippedExistingRecordCount())
                .skippedActiveTaskCount(summary.skippedActiveTaskCount())
                .invalidCount(invalidCount)
                .createdItems(summary.createdItems())
                .build();
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.ImportFromCardLinksResponse importFromCardLinks(
            Alibaba1688DetailTaskDTO.ImportFromCardLinksRequest request
    ) {
        Alibaba1688AuthSession credential = resolveCredential(request == null ? null : request.getCredentialId());
        boolean forceRefresh = Boolean.TRUE.equals(request == null ? null : request.getForceRefresh());
        boolean allMatching = Boolean.TRUE.equals(request == null ? null : request.getAllMatching());

        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
        List<Alibaba1688CardLinkRecord> selectedRecords = new ArrayList<>();
        if (allMatching) {
            Specification<Alibaba1688CardLinkRecord> spec = buildCardLinkSpec(
                    request == null ? null : request.getKeyword(),
                    request == null ? null : request.getType(),
                    request == null ? null : request.getStatus()
            );
            selectedRecords.addAll(cardLinkRecordRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")));
            for (Alibaba1688CardLinkRecord record : selectedRecords) {
                if (record != null && record.getId() != null) {
                    selectedIds.add(record.getId());
                }
            }
            if (selectedRecords.isEmpty()) {
                throw new IllegalArgumentException("当前筛选条件下没有可导入的卡片链接");
            }
        } else {
            if (request != null && request.getCardLinkIds() != null) {
                for (Long id : request.getCardLinkIds()) {
                    if (id != null) {
                        selectedIds.add(id);
                    }
                }
            }
            if (selectedIds.isEmpty()) {
                throw new IllegalArgumentException("请至少选择 1 条未处理卡片链接");
            }

            Map<Long, Alibaba1688CardLinkRecord> recordMap = new HashMap<>();
            cardLinkRecordRepository.findAllById(selectedIds).forEach(item -> recordMap.put(item.getId(), item));
            for (Long id : selectedIds) {
                Alibaba1688CardLinkRecord record = recordMap.get(id);
                if (record != null) {
                    selectedRecords.add(record);
                }
            }
        }

        List<Alibaba1688CardLinkRecord> processedRecords = new ArrayList<>();
        LinkedHashMap<String, ParsedTaskCandidate> deduped = new LinkedHashMap<>();
        int skippedUnavailableCount = 0;
        for (Alibaba1688CardLinkRecord record : selectedRecords) {
            if (record == null || record.getStatus() == null || record.getStatus() != 0 || !StringUtils.hasText(record.getOfferId())) {
                skippedUnavailableCount++;
                continue;
            }
            processedRecords.add(record);
            String offerId = trimToNull(record.getOfferId());
            deduped.putIfAbsent(offerId, buildCandidateFromCardLink(record));
        }

        CreateTaskSummary summary = deduped.isEmpty()
                ? new CreateTaskSummary(0, 0, 0, List.of())
                : createTasks(credential, deduped.values(), forceRefresh, "CARD_LINK_IMPORT");

        if (!processedRecords.isEmpty()) {
            for (Alibaba1688CardLinkRecord record : processedRecords) {
                record.setStatus(1);
            }
            cardLinkRecordRepository.saveAll(processedRecords);
        }

        return Alibaba1688DetailTaskDTO.ImportFromCardLinksResponse.builder()
                .selectedCount(selectedIds.size())
                .availableCount(deduped.size())
                .createdCount(summary.createdCount())
                .skippedExistingRecordCount(summary.skippedExistingRecordCount())
                .skippedActiveTaskCount(summary.skippedActiveTaskCount())
                .skippedUnavailableCount(skippedUnavailableCount)
                .processedCount(processedRecords.size())
                .createdItems(summary.createdItems())
                .build();
    }

    private Specification<Alibaba1688CardLinkRecord> buildCardLinkSpec(String keyword, String type, Integer status) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            if (StringUtils.hasText(type)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("type")), type.trim().toLowerCase()));
            }

            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("offerId")), likeValue),
                        cb.like(cb.lower(root.get("detailUrl")), likeValue),
                        cb.like(cb.lower(root.get("cardHref")), likeValue),
                        cb.like(cb.lower(root.get("renderKey")), likeValue),
                        cb.like(cb.lower(root.get("cardClass")), likeValue)
                ));
            }

            return predicate;
        };
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.WorkerClaimResponse claimNext(Alibaba1688DetailTaskDTO.WorkerClaimRequest request) {
        Long credentialId = request == null ? null : request.getCredentialId();
        String workerName = trimToNull(request == null ? null : request.getWorkerName());
        List<Alibaba1688DetailTask> tasks = taskRepository.lockPendingTasks(credentialId, PageRequest.of(0, 1));
        if (tasks.isEmpty()) {
            return Alibaba1688DetailTaskDTO.WorkerClaimResponse.builder()
                    .claimed(false)
                    .task(null)
                    .build();
        }

        Alibaba1688DetailTask task = tasks.get(0);
        task.setStatus("RUNNING");
        task.setAttemptCount((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
        task.setWorkerName(workerName);
        task.setClaimToken(UUID.randomUUID().toString());
        if (task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        task.setLastError(null);
        Alibaba1688DetailTask saved = taskRepository.save(task);
        String credentialName = authSessionRepository.findById(saved.getCredentialId())
                .map(Alibaba1688AuthSession::getSessionName)
                .orElse(null);
        return Alibaba1688DetailTaskDTO.WorkerClaimResponse.builder()
                .claimed(true)
                .task(toListItem(saved, credentialName))
                .build();
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.ListItem reportSuccess(Long taskId, Alibaba1688DetailTaskDTO.WorkerSuccessRequest request) {
        Alibaba1688DetailTask task = loadTask(taskId);
        validateClaimToken(task, request == null ? null : request.getClaimToken());
        Alibaba1688AuthSession credential = authSessionRepository.findById(task.getCredentialId())
                .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + task.getCredentialId()));

        String html = request == null ? null : request.getHtml();
        if (!StringUtils.hasText(html)) {
            throw new IllegalArgumentException("html 不能为空");
        }

        Alibaba1688DetailRecord record = recordRepository.findByOfferId(task.getOfferId())
                .orElseGet(Alibaba1688DetailRecord::new);
        try {
            Alibaba1688HtmlParser.ParsedProduct parsed = new Alibaba1688HtmlParser(objectMapper).parse(html);
            validateSuccessfulDetailPage(task, request, parsed);
            String parsedJson = objectMapper.writeValueAsString(parsed);

            record.setOfferId(task.getOfferId());
            record.setDetailUrl(task.getDetailUrl());
            record.setCanonicalUrl(trimToNull(request == null ? null : request.getFinalUrl()) != null
                    ? trimToNull(request.getFinalUrl())
                    : parsed.getProductUrl());
            record.setProductName(parsed.getProductName());
            record.setCompanyName(parsed.getCompanyName());
            record.setProductMainImage(parsed.getProductMainImage());
            record.setMinPrice(parsed.getMinPrice());
            record.setMaxPrice(parsed.getMaxPrice());
            record.setRepeatCustomerRate(parsed.getRepeatCustomerRate());
            record.setServiceScore(parsed.getServiceScore());
            record.setOnTimeDeliveryRate(parsed.getOnTimeDeliveryRate());
            record.setShopPositiveRate(parsed.getShopPositiveRate());
            record.setPowerSeller(parsed.getPowerSeller());
            record.setSettledYearsText(parsed.getSettledYearsText());
            record.setMainBusiness(parsed.getMainBusiness());
            record.setSourcePlatform("1688");
            record.setStatus("READY");
            record.setLastError(null);
            record.setLastTaskId(task.getId());
            record.setLastCredentialId(task.getCredentialId());
            record.setRawHtml(html);
            record.setExtractedJson(trimToNull(request == null ? null : request.getExtractedJson()));
            record.setParsedJson(parsedJson);
            record.setLastCollectedAt(LocalDateTime.now());
            Alibaba1688DetailRecord savedRecord = recordRepository.save(record);

            task.setStatus("SUCCEEDED");
            task.setDetailRecordId(savedRecord.getId());
            task.setFinishedAt(LocalDateTime.now());
            task.setLastError(null);
            task.setClaimToken(null);
            taskRepository.save(task);

            credential.setStatus("ACTIVE");
            credential.setLastVerifiedAt(LocalDateTime.now());
            credential.setLastError(null);
            authSessionRepository.save(credential);

            cardLinkRecordRepository.findByOfferId(task.getOfferId()).ifPresent(card -> {
                card.setStatus(1);
                cardLinkRecordRepository.save(card);
            });

            return toListItem(task, credential.getSessionName());
        } catch (Exception e) {
            if (record.getId() == null || !"READY".equalsIgnoreCase(record.getStatus())) {
                record.setOfferId(task.getOfferId());
                record.setDetailUrl(task.getDetailUrl());
                record.setCanonicalUrl(trimToNull(request == null ? null : request.getFinalUrl()));
                record.setStatus("FAILED");
                record.setLastError(e.getMessage());
                record.setLastTaskId(task.getId());
                record.setLastCredentialId(task.getCredentialId());
                record.setRawHtml(html);
                record.setExtractedJson(trimToNull(request == null ? null : request.getExtractedJson()));
                record.setLastCollectedAt(LocalDateTime.now());
                recordRepository.save(record);
            }

            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            task.setLastError("详情解析失败: " + e.getMessage());
            task.setClaimToken(null);
            taskRepository.save(task);
            throw new IllegalStateException("详情解析失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.ListItem reportFailure(Long taskId, Alibaba1688DetailTaskDTO.WorkerFailureRequest request) {
        Alibaba1688DetailTask task = loadTask(taskId);
        validateClaimToken(task, request == null ? null : request.getClaimToken());
        Alibaba1688AuthSession credential = authSessionRepository.findById(task.getCredentialId())
                .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + task.getCredentialId()));

        String errorMessage = trimToNull(request == null ? null : request.getErrorMessage());
        task.setStatus("FAILED");
        task.setFinishedAt(LocalDateTime.now());
        task.setLastError(errorMessage != null ? errorMessage : "worker 未返回失败原因");
        task.setClaimToken(null);
        Alibaba1688DetailTask savedTask = taskRepository.save(task);

        if (Boolean.TRUE.equals(request == null ? null : request.getAuthExpired())) {
            credential.setStatus("EXPIRED");
            credential.setLastError(errorMessage);
            credential.setLastVerifiedAt(LocalDateTime.now());
            authSessionRepository.save(credential);
        }

        return toListItem(savedTask, credential.getSessionName());
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.ListItem retryTask(Long id) {
        Alibaba1688DetailTask task = loadTask(id);
        task.setStatus("PENDING");
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setWorkerName(null);
        task.setClaimToken(null);
        task.setLastError(null);
        Alibaba1688DetailTask saved = taskRepository.save(task);
        String credentialName = authSessionRepository.findById(saved.getCredentialId())
                .map(Alibaba1688AuthSession::getSessionName)
                .orElse(null);
        return toListItem(saved, credentialName);
    }

    @Transactional
    public Alibaba1688DetailTaskDTO.ListItem cancelTask(Long id) {
        Alibaba1688DetailTask task = loadTask(id);
        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        task.setClaimToken(null);
        Alibaba1688DetailTask saved = taskRepository.save(task);
        String credentialName = authSessionRepository.findById(saved.getCredentialId())
                .map(Alibaba1688AuthSession::getSessionName)
                .orElse(null);
        return toListItem(saved, credentialName);
    }

    private Alibaba1688AuthSession resolveCredential(Long credentialId) {
        if (credentialId == null) {
            throw new IllegalArgumentException("请选择 1688 凭证");
        }
        Alibaba1688AuthSession credential = authSessionRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + credentialId));
        if (!Boolean.TRUE.equals(credential.getEnabled())) {
            throw new IllegalArgumentException("1688 凭证已停用，不能下发任务");
        }
        if (!StringUtils.hasText(credential.getStorageStateEncrypted())) {
            throw new IllegalArgumentException("1688 凭证还没有登录态，请先用 worker 保存一次登录凭证");
        }
        return credential;
    }

    private CreateTaskSummary createTasks(
            Alibaba1688AuthSession credential,
            Collection<ParsedTaskCandidate> candidates,
            boolean forceRefresh,
            String sourceType
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new CreateTaskSummary(0, 0, 0, List.of());
        }

        Set<String> offerIds = candidates.stream()
                .map(ParsedTaskCandidate::offerId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (offerIds.isEmpty()) {
            return new CreateTaskSummary(0, 0, 0, List.of());
        }

        Map<String, Alibaba1688DetailRecord> existingRecordMap = recordRepository.findAllByOfferIdIn(offerIds).stream()
                .collect(Collectors.toMap(Alibaba1688DetailRecord::getOfferId, item -> item));
        Map<String, Alibaba1688DetailTask> existingTaskMap = taskRepository
                .findAllByCredentialIdAndOfferIdIn(credential.getId(), offerIds)
                .stream()
                .collect(Collectors.toMap(Alibaba1688DetailTask::getOfferId, item -> item, (left, right) -> left));

        List<Alibaba1688DetailTask> toSave = new ArrayList<>();
        int skippedExistingRecordCount = 0;
        int skippedActiveTaskCount = 0;

        for (ParsedTaskCandidate candidate : candidates) {
            if (!StringUtils.hasText(candidate.offerId())) {
                continue;
            }
            Alibaba1688DetailRecord existingRecord = existingRecordMap.get(candidate.offerId());
            if (!forceRefresh && existingRecord != null && "READY".equalsIgnoreCase(existingRecord.getStatus())) {
                skippedExistingRecordCount++;
                continue;
            }

            Alibaba1688DetailTask existingTask = existingTaskMap.get(candidate.offerId());
            if (existingTask != null && ACTIVE_TASK_STATUSES.contains(String.valueOf(existingTask.getStatus()).toUpperCase())) {
                skippedActiveTaskCount++;
                continue;
            }

            Alibaba1688DetailTask task = existingTask != null ? existingTask : new Alibaba1688DetailTask();
            task.setCredentialId(credential.getId());
            task.setOfferId(candidate.offerId());
            task.setDetailUrl(candidate.detailUrl());
            task.setSourceType(sourceType);
            task.setStatus("PENDING");
            task.setWorkerName(null);
            task.setClaimToken(null);
            task.setStartedAt(null);
            task.setFinishedAt(null);
            task.setLastError(null);
            task.setDetailRecordId(null);
            if (task.getAttemptCount() == null) {
                task.setAttemptCount(0);
            }
            toSave.add(task);
        }

        List<Alibaba1688DetailTaskDTO.ListItem> createdItems = new ArrayList<>();
        if (!toSave.isEmpty()) {
            try {
                List<Alibaba1688DetailTask> saved = taskRepository.saveAll(toSave);
                for (Alibaba1688DetailTask item : saved) {
                    createdItems.add(toListItem(item, credential.getSessionName()));
                }
            } catch (DataIntegrityViolationException e) {
                throw new IllegalStateException("检测到并发重复任务，请刷新列表后重试", e);
            }
        }

        return new CreateTaskSummary(
                createdItems.size(),
                skippedExistingRecordCount,
                skippedActiveTaskCount,
                createdItems
        );
    }

    private ParsedTaskCandidate buildCandidateFromCardLink(Alibaba1688CardLinkRecord record) {
        String offerId = trimToNull(record == null ? null : record.getOfferId());
        String preferredUrl = firstText(
                trimToNull(record == null ? null : record.getDetailUrl()),
                trimToNull(record == null ? null : record.getCardHref())
        );
        return new ParsedTaskCandidate(offerId, buildCanonicalDetailUrl(offerId, preferredUrl));
    }

    private Alibaba1688DetailTask loadTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 详情任务不存在: " + id));
    }

    private Map<Long, String> loadCredentialNameMap(Set<Long> ids) {
        Set<Long> validIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Alibaba1688AuthSession item : authSessionRepository.findAllById(validIds)) {
            map.put(item.getId(), item.getSessionName());
        }
        return map;
    }

    private Alibaba1688DetailTaskDTO.ListItem toListItem(Alibaba1688DetailTask entity, String credentialName) {
        return Alibaba1688DetailTaskDTO.ListItem.builder()
                .id(entity.getId())
                .credentialId(entity.getCredentialId())
                .credentialName(credentialName)
                .claimToken(entity.getClaimToken())
                .offerId(entity.getOfferId())
                .detailUrl(entity.getDetailUrl())
                .sourceType(entity.getSourceType())
                .status(entity.getStatus())
                .attemptCount(entity.getAttemptCount())
                .workerName(entity.getWorkerName())
                .lastError(entity.getLastError())
                .detailRecordId(entity.getDetailRecordId())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<ParsedTaskCandidate> parseTaskCandidates(String rawInput) {
        if (!StringUtils.hasText(rawInput)) {
            throw new IllegalArgumentException("请粘贴至少 1 个 1688 详情链接");
        }
        List<ParsedTaskCandidate> result = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(rawInput);
        while (matcher.find()) {
            String rawUrl = cleanupUrlTail(matcher.group());
            String offerId = extractOfferId(rawUrl);
            result.add(new ParsedTaskCandidate(offerId, buildCanonicalDetailUrl(offerId, rawUrl)));
        }
        return result;
    }

    private String cleanupUrlTail(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return rawUrl;
        }
        return rawUrl.replaceAll("[),.;，。；]+$", "").trim();
    }

    private String extractOfferId(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        Matcher matcher = OFFER_ID_IN_URL_PATTERN.matcher(rawUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String buildCanonicalDetailUrl(String offerId, String rawUrl) {
        if (StringUtils.hasText(offerId)) {
            return "https://detail.1688.com/offer/" + offerId + ".html";
        }
        return rawUrl;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void validateSuccessfulDetailPage(
            Alibaba1688DetailTask task,
            Alibaba1688DetailTaskDTO.WorkerSuccessRequest request,
            Alibaba1688HtmlParser.ParsedProduct parsed
    ) {
        String expectedOfferId = trimToNull(task == null ? null : task.getOfferId());
        String parsedOfferId = firstText(
                parsed == null ? null : parsed.getProductId(),
                parsed == null ? null : parsed.getAlibabaProductId()
        );
        if (StringUtils.hasText(expectedOfferId) && StringUtils.hasText(parsedOfferId) && !Objects.equals(expectedOfferId, parsedOfferId)) {
            throw new IllegalStateException("详情页 offerId 不匹配，expected=" + expectedOfferId + ", parsed=" + parsedOfferId);
        }

        JsonNode extractedRoot = readJsonNode(request == null ? null : request.getExtractedJson());
        String finalUrl = trimToNull(request == null ? null : request.getFinalUrl());
        String extractedPageUrl = trimToNull(readJsonText(extractedRoot, "pageUrl"));
        String extractedCanonicalUrl = trimToNull(readJsonText(extractedRoot, "canonicalUrl"));
        String parsedProductUrl = trimToNull(parsed == null ? null : parsed.getProductUrl());

        boolean anyMatchedUrl = containsExpectedOfferId(finalUrl, expectedOfferId)
                || containsExpectedOfferId(extractedPageUrl, expectedOfferId)
                || containsExpectedOfferId(extractedCanonicalUrl, expectedOfferId)
                || containsExpectedOfferId(parsedProductUrl, expectedOfferId);
        if (StringUtils.hasText(expectedOfferId) && !anyMatchedUrl) {
            throw new IllegalStateException("详情页疑似跳转到非目标商品页面，expectedOfferId=" + expectedOfferId
                    + ", finalUrl=" + firstText(finalUrl, extractedPageUrl, extractedCanonicalUrl, parsedProductUrl));
        }

        String effectiveTitle = firstText(
                parsed == null ? null : parsed.getProductName(),
                request == null ? null : request.getPageTitle(),
                readJsonText(extractedRoot, "title"),
                readJsonText(extractedRoot, "pageTitle")
        );
        if (isKnownInvalid1688Title(effectiveTitle)) {
            throw new IllegalStateException("详情页疑似落到 1688 首页/无效页，title=" + effectiveTitle);
        }
    }

    private JsonNode readJsonNode(String rawJson) {
        String normalized = trimToNull(rawJson);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readJsonText(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return trimToNull(child.asText());
    }

    private boolean containsExpectedOfferId(String url, String expectedOfferId) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(expectedOfferId)) {
            return false;
        }
        return Objects.equals(expectedOfferId, extractOfferId(url));
    }

    private boolean isKnownInvalid1688Title(String title) {
        String normalized = trimToNull(title);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.contains("阿里1688首页")
                || normalized.contains("1688首页")
                || normalized.contains("全球领先的采购批发平台");
    }

    private void validateClaimToken(Alibaba1688DetailTask task, String claimToken) {
        if (!"RUNNING".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalStateException("任务不在 RUNNING 状态，不能回写结果");
        }
        if (!StringUtils.hasText(task.getClaimToken()) || !Objects.equals(task.getClaimToken(), trimToNull(claimToken))) {
            throw new IllegalStateException("任务 claimToken 已失效，请忽略这次回写");
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private record ParsedTaskCandidate(String offerId, String detailUrl) {
    }

    private record CreateTaskSummary(
            int createdCount,
            int skippedExistingRecordCount,
            int skippedActiveTaskCount,
            List<Alibaba1688DetailTaskDTO.ListItem> createdItems
    ) {
    }
}
