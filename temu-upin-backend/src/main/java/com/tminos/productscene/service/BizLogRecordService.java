package com.tminos.productscene.service;

import com.tminos.productscene.entity.BizLogRecord;
import com.tminos.productscene.repository.BizLogRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BizLogRecordService {

    private final BizLogRecordRepository repo;

    public BizLogRecordService(BizLogRecordRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<BizLogRecord> list(String bizName, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        Specification<BizLogRecord> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            if (StringUtils.hasText(bizName)) {
                p = cb.and(p, cb.like(root.get("bizName"), "%" + bizName.trim() + "%"));
            }
            return p;
        };
        return repo.findAll(spec, pageable);
    }

    @Transactional
    public BizLogRecord create(String bizName, String content) {
        if (!StringUtils.hasText(bizName)) {
            throw new IllegalArgumentException("bizName is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        BizLogRecord r = new BizLogRecord();
        r.setBizName(bizName.trim());
        r.setContent(content);
        return repo.save(r);
    }

    @Transactional
    public BizLogRecord update(Long id, String bizName, String content) {
        BizLogRecord r = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("biz log not found: " + id));
        if (bizName != null) {
            if (!StringUtils.hasText(bizName)) throw new IllegalArgumentException("bizName cannot be blank");
            r.setBizName(bizName.trim());
        }
        if (content != null) {
            r.setContent(content);
        }
        return repo.save(r);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
