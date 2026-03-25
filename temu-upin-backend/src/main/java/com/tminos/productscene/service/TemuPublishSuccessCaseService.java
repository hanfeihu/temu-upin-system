package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuPublishSuccessCaseDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.TemuPublishSuccessCase;
import com.tminos.productscene.repository.TemuPublishSuccessCaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TemuPublishSuccessCaseService {

    private final TemuPublishSuccessCaseRepository successCaseRepository;

    public TemuPublishSuccessCaseService(TemuPublishSuccessCaseRepository successCaseRepository) {
        this.successCaseRepository = successCaseRepository;
    }

    @Transactional
    public void recordSuccess(Long runId, ProductCollection product, String goodsId, String requestJson, String responseRaw) {
        if (runId == null || product == null || !StringUtils.hasText(requestJson) || !StringUtils.hasText(responseRaw)) {
            return;
        }

        TemuPublishSuccessCase entity = successCaseRepository.findByPublishRunId(runId).orElseGet(TemuPublishSuccessCase::new);
        entity.setPublishRunId(runId);
        entity.setSpuId(product.getId());
        entity.setProductId(product.getProductId());
        entity.setProductName(product.getProductName());
        entity.setTemuCatid(product.getTemuCatid());
        entity.setTemuCatname(product.getTemuCatname());
        entity.setGoodsId(goodsId);
        entity.setRequestJson(requestJson);
        entity.setResponseRaw(responseRaw);
        entity.setPublishedAt(product.getTemuPublishedAt() != null ? product.getTemuPublishedAt() : LocalDateTime.now());
        successCaseRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TemuPublishSuccessCaseDTO.Row> list(Long spuId, String temuCatid) {
        List<TemuPublishSuccessCase> cases;
        if (spuId != null && StringUtils.hasText(temuCatid)) {
            cases = successCaseRepository.findBySpuIdAndTemuCatidOrderByIdDesc(spuId, temuCatid);
        } else if (spuId != null) {
            cases = successCaseRepository.findBySpuIdOrderByIdDesc(spuId);
        } else if (StringUtils.hasText(temuCatid)) {
            cases = successCaseRepository.findByTemuCatidOrderByIdDesc(temuCatid);
        } else {
            cases = successCaseRepository.findTop100ByOrderByIdDesc();
        }
        return cases.stream().map(TemuPublishSuccessCaseDTO.Row::from).toList();
    }

    @Transactional(readOnly = true)
    public TemuPublishSuccessCaseDTO.Detail get(Long id) {
        Long caseId = Objects.requireNonNull(id, "id");
        TemuPublishSuccessCase entity = successCaseRepository.findById(caseId)
                .orElseThrow(() -> new EntityNotFoundException("publish success case not found: " + id));
        return TemuPublishSuccessCaseDTO.Detail.from(entity);
    }
}