package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.DianxiaomiPackageFeeDTO;
import com.tminos.productscene.entity.DianxiaomiPackageFeeRecord;
import com.tminos.productscene.entity.LogisticsProviderConfig;
import com.tminos.productscene.repository.DianxiaomiPackageFeeRecordRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DianxiaomiPackageFeeService {

    private static final BigDecimal PACKING_FEE_PER_SUCCESS_RECORD = BigDecimal.valueOf(2);

    private final DianxiaomiPackageFeeRecordRepository repository;
    private final LogisticsProviderConfigService logisticsProviderConfigService;
    private final HaoyuanLogisticsClient haoyuanLogisticsClient;
    private final ObjectMapper objectMapper;

    public DianxiaomiPackageFeeService(DianxiaomiPackageFeeRecordRepository repository,
                                       LogisticsProviderConfigService logisticsProviderConfigService,
                                       HaoyuanLogisticsClient haoyuanLogisticsClient,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.logisticsProviderConfigService = logisticsProviderConfigService;
        this.haoyuanLogisticsClient = haoyuanLogisticsClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<DianxiaomiPackageFeeDTO.ListItem> list(String keyword, int page, int pageSize) {
        String normalizedKeyword = trim(keyword);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
        );
        return repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(normalizedKeyword)) {
                predicates.add(cb.like(cb.lower(root.get("dianxiaomiPackageNumber")), "%" + normalizedKeyword.toLowerCase() + "%"));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        }, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public DianxiaomiPackageFeeDTO.Summary summary(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        BigDecimal totalFee = repository.sumTotalFeeByKeyword(normalizedKeyword);
        long successCount = repository.countSuccessByKeyword(normalizedKeyword);
        BigDecimal packingFee = PACKING_FEE_PER_SUCCESS_RECORD.multiply(BigDecimal.valueOf(successCount));
        BigDecimal safeTotalFee = totalFee == null ? BigDecimal.ZERO : totalFee;
        return DianxiaomiPackageFeeDTO.Summary.builder()
                .totalCount(repository.countByKeyword(normalizedKeyword))
                .successCount(successCount)
                .failedCount(repository.countFailedByKeyword(normalizedKeyword))
                .totalFee(safeTotalFee)
                .packingFee(packingFee)
                .totalFeeWithPacking(safeTotalFee.add(packingFee))
                .build();
    }

    @Transactional
    public long clearAll() {
        long deletedCount = repository.count();
        if (deletedCount > 0) {
            repository.deleteAllInBatch();
        }
        return deletedCount;
    }

    @Transactional
    public long clearSuccessRecords() {
        return repository.deleteSuccessRecords();
    }

    @Transactional
    public DianxiaomiPackageFeeDTO.ImportResult retryFailedRecords() {
        List<String> packageNumbers = repository.findFailedRecords().stream()
                .map(DianxiaomiPackageFeeRecord::getDianxiaomiPackageNumber)
                .filter(StringUtils::hasText)
                .toList();
        if (packageNumbers.isEmpty()) {
            return DianxiaomiPackageFeeDTO.ImportResult.builder()
                    .inputCount(0)
                    .acceptedCount(0)
                    .createdCount(0)
                    .updatedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .failures(List.of())
                    .build();
        }
        return importPackageNumbers(packageNumbers);
    }

    @Transactional
    public DianxiaomiPackageFeeDTO.ImportResult retryRecord(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("记录 ID 不能为空");
        }
        DianxiaomiPackageFeeRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        if (!StringUtils.hasText(record.getErrorMessage())) {
            throw new IllegalStateException("当前记录不是失败状态，无需重试");
        }
        return importPackageNumbers(List.of(record.getDianxiaomiPackageNumber()));
    }

    @Transactional
    public DianxiaomiPackageFeeDTO.ImportResult importPackageNumbers(DianxiaomiPackageFeeDTO.ImportRequest request) {
        List<String> rawPackageNumbers = request == null ? null : request.getPackageNumbers();
        return importPackageNumbers(rawPackageNumbers);
    }

    private DianxiaomiPackageFeeDTO.ImportResult importPackageNumbers(List<String> rawPackageNumbers) {
        int inputCount = rawPackageNumbers == null ? 0 : rawPackageNumbers.size();
        List<String> packageNumbers = normalizePackageNumbers(rawPackageNumbers);
        if (packageNumbers.isEmpty()) {
            throw new IllegalArgumentException("请至少粘贴 1 个店小秘单号");
        }

        LogisticsProviderConfig config = logisticsProviderConfigService.getEnabledByCodeOrThrow(LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE);
        int createdCount = 0;
        int updatedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        List<DianxiaomiPackageFeeDTO.ImportFailure> failures = new ArrayList<>();

        for (String packageNumber : packageNumbers) {
            DianxiaomiPackageFeeRecord entity = repository.findByDianxiaomiPackageNumber(packageNumber)
                    .orElseGet(DianxiaomiPackageFeeRecord::new);
            boolean created = entity.getId() == null;
            entity.setDianxiaomiPackageNumber(packageNumber);
            entity.setLastQueriedAt(LocalDateTime.now());

            try {
                JsonNode root = haoyuanLogisticsClient.sendRequest(
                        config,
                        "getbusinessfee_detail",
                        objectMapper.writeValueAsString(Map.of("reference_no", packageNumber))
                );
                JsonNode data = root.path("data");
                BigDecimal totalFee = resolveTotalFee(data);
                List<DianxiaomiPackageFeeDTO.FeeDetailItem> feeDetailItems = parseFeeDetailItems(data);
                if (totalFee == null && feeDetailItems.isEmpty()) {
                    throw new IllegalStateException("浩远未返回费用明细");
                }
                entity.setTotalFee(totalFee);
                entity.setFeeDetailJson(data.isMissingNode() || data.isNull() ? null : data.toString());
                entity.setRawResponseJson(root.toString());
                entity.setErrorMessage(null);
                successCount++;
            } catch (Exception e) {
                entity.setTotalFee(null);
                entity.setFeeDetailJson(null);
                entity.setRawResponseJson(writeJson(Map.of("error", resolveMessage(e))));
                entity.setErrorMessage(resolveMessage(e));
                failedCount++;
                failures.add(DianxiaomiPackageFeeDTO.ImportFailure.builder()
                        .dianxiaomiPackageNumber(packageNumber)
                        .message(resolveMessage(e))
                        .build());
            }

            repository.save(entity);
            if (created) {
                createdCount++;
            } else {
                updatedCount++;
            }
        }

        return DianxiaomiPackageFeeDTO.ImportResult.builder()
                .inputCount(inputCount)
                .acceptedCount(packageNumbers.size())
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .failures(failures)
                .build();
    }

    private DianxiaomiPackageFeeDTO.ListItem toListItem(DianxiaomiPackageFeeRecord entity) {
        boolean success = isSuccess(entity);
        BigDecimal packingFee = success ? PACKING_FEE_PER_SUCCESS_RECORD : BigDecimal.ZERO;
        BigDecimal totalFeeWithPacking = entity.getTotalFee() == null ? null : entity.getTotalFee().add(packingFee);
        return DianxiaomiPackageFeeDTO.ListItem.builder()
                .id(entity.getId())
                .dianxiaomiPackageNumber(entity.getDianxiaomiPackageNumber())
                .totalFee(entity.getTotalFee())
                .packingFee(packingFee)
                .totalFeeWithPacking(totalFeeWithPacking)
                .errorMessage(entity.getErrorMessage())
                .feeDetailItems(parseFeeDetailItems(entity.getFeeDetailJson()))
                .lastQueriedAt(entity.getLastQueriedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private boolean isSuccess(DianxiaomiPackageFeeRecord entity) {
        return entity != null && entity.getTotalFee() != null && !StringUtils.hasText(entity.getErrorMessage());
    }

    private List<DianxiaomiPackageFeeDTO.FeeDetailItem> parseFeeDetailItems(String feeDetailJson) {
        if (!StringUtils.hasText(feeDetailJson)) {
            return List.of();
        }
        try {
            return parseFeeDetailItems(objectMapper.readTree(feeDetailJson));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<DianxiaomiPackageFeeDTO.FeeDetailItem> parseFeeDetailItems(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<DianxiaomiPackageFeeDTO.FeeDetailItem> items = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode child : node) {
                DianxiaomiPackageFeeDTO.FeeDetailItem item = toFeeDetailItem(child);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        }
        DianxiaomiPackageFeeDTO.FeeDetailItem item = toFeeDetailItem(node);
        return item == null ? List.of() : List.of(item);
    }

    private DianxiaomiPackageFeeDTO.FeeDetailItem toFeeDetailItem(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return null;
        }
        return DianxiaomiPackageFeeDTO.FeeDetailItem.builder()
                .feeKindCode(text(node, "fee_kind_code"))
                .feeKindName(text(node, "fee_kind_name"))
                .amount(decimal(text(node, "amount")))
                .currencyAmount(decimal(text(node, "currency_amount")))
                .currencyCode(text(node, "currency_code"))
                .currencyName(text(node, "currency_name"))
                .currencyRate(decimal(text(node, "currency_rate")))
                .note(text(node, "note"))
                .occurDate(text(node, "occur_date"))
                .billDate(text(node, "bill_date"))
                .createDate(text(node, "create_date"))
                .build();
    }

    private BigDecimal resolveTotalFee(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return null;
        }
        if (data.isArray()) {
            BigDecimal total = null;
            for (JsonNode child : data) {
                BigDecimal amount = firstAvailableDecimal(child, "currency_amount", "amount", "business_fee", "fee", "price");
                if (amount == null) {
                    continue;
                }
                total = total == null ? amount : total.add(amount);
            }
            return total;
        }
        return firstAvailableDecimal(data, "total_fee", "pay_fee", "fee_total", "totalAmount", "currency_amount", "amount", "business_fee", "fee", "price");
    }

    private List<String> normalizePackageNumbers(List<String> rawPackageNumbers) {
        if (rawPackageNumbers == null || rawPackageNumbers.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String rawPackageNumber : rawPackageNumbers) {
            String normalized = trim(rawPackageNumber);
            if (StringUtils.hasText(normalized)) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String normalizeKeyword(String keyword) {
        String normalized = trim(keyword);
        return StringUtils.hasText(normalized) ? "%" + normalized.toLowerCase() + "%" : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String resolveMessage(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = trim(current.getMessage());
        if (StringUtils.hasText(message)) {
            return message;
        }
        message = trim(error.getMessage());
        return StringUtils.hasText(message) ? message : error.getClass().getSimpleName();
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? null : trim(field.asText());
    }

    private static BigDecimal firstAvailableDecimal(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            BigDecimal value = decimal(text(node, fieldName));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
