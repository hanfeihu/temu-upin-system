package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688AuthSessionDTO;
import com.tminos.productscene.entity.Alibaba1688AuthSession;
import com.tminos.productscene.repository.Alibaba1688AuthSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class Alibaba1688AuthSessionService {

    private final Alibaba1688AuthSessionRepository repository;
    private final Alibaba1688CryptoService cryptoService;

    public Alibaba1688AuthSessionService(
            Alibaba1688AuthSessionRepository repository,
            Alibaba1688CryptoService cryptoService
    ) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public Page<Alibaba1688AuthSessionDTO.ListItem> list(String keyword, Boolean enabled, String status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")
        );
        Specification<Alibaba1688AuthSession> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (enabled != null) {
                predicate = cb.and(predicate, cb.equal(root.get("enabled"), enabled));
            }
            if (StringUtils.hasText(status)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase()));
            }
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("sessionName")), likeValue),
                        cb.like(cb.lower(root.get("accountNick")), likeValue),
                        cb.like(cb.lower(root.get("memberId")), likeValue),
                        cb.like(cb.lower(root.get("remark")), likeValue)
                ));
            }
            return predicate;
        };
        return repository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public List<Alibaba1688AuthSessionDTO.OptionItem> listOptions(Boolean enabledOnly) {
        Specification<Alibaba1688AuthSession> spec = (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("id")));
            if (Boolean.TRUE.equals(enabledOnly)) {
                return cb.isTrue(root.get("enabled"));
            }
            return cb.conjunction();
        };
        return repository.findAll(spec).stream().map(this::toOptionItem).toList();
    }

    @Transactional
    public Alibaba1688AuthSessionDTO.ListItem save(Long id, Alibaba1688AuthSessionDTO.SaveRequest request) {
        Alibaba1688AuthSession entity = id == null
                ? new Alibaba1688AuthSession()
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + id));

        String sessionName = trimToNull(request == null ? null : request.getSessionName());
        if (!StringUtils.hasText(sessionName)) {
            throw new IllegalArgumentException("会话名称不能为空");
        }
        validateUniqueSessionName(sessionName, entity.getId());

        entity.setSessionName(sessionName);
        entity.setRemark(trimToNull(request == null ? null : request.getRemark()));
        if (request != null && request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(entity.getStorageStateEncrypted() == null ? "EMPTY" : "ACTIVE");
        }
        return toListItem(repository.save(entity));
    }

    @Transactional
    public Alibaba1688AuthSessionDTO.ListItem toggleEnabled(Long id, boolean enabled) {
        Alibaba1688AuthSession entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + id));
        entity.setEnabled(enabled);
        return toListItem(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Alibaba1688AuthSessionDTO.StorageStateResponse getWorkerStorageState(Long id) {
        Alibaba1688AuthSession entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + id));
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("1688 凭证已停用，不能被 worker 使用");
        }
        if (!StringUtils.hasText(entity.getStorageStateEncrypted())) {
            throw new IllegalArgumentException("1688 凭证尚未保存登录态");
        }
        return Alibaba1688AuthSessionDTO.StorageStateResponse.builder()
                .id(entity.getId())
                .sessionName(entity.getSessionName())
                .homeUrl(entity.getHomeUrl())
                .storageStateJson(cryptoService.decrypt(entity.getStorageStateEncrypted()))
                .build();
    }

    @Transactional
    public Alibaba1688AuthSessionDTO.WorkerSaveResponse workerSaveStorage(Alibaba1688AuthSessionDTO.WorkerSaveRequest request) {
        String storageStateJson = trimToNull(request == null ? null : request.getStorageStateJson());
        if (!StringUtils.hasText(storageStateJson)) {
            throw new IllegalArgumentException("storageStateJson 不能为空");
        }

        Alibaba1688AuthSession entity = resolveForWorkerSave(request);
        String sessionName = trimToNull(request == null ? null : request.getSessionName());
        if (StringUtils.hasText(sessionName)) {
            validateUniqueSessionName(sessionName, entity.getId());
            entity.setSessionName(sessionName);
        }
        if (!StringUtils.hasText(entity.getSessionName())) {
            throw new IllegalArgumentException("会话名称不能为空");
        }

        entity.setAccountNick(trimToNull(request.getAccountNick()));
        entity.setMemberId(trimToNull(request.getMemberId()));
        entity.setHomeUrl(trimToNull(request.getHomeUrl()));
        if (request.getRemark() != null) {
            entity.setRemark(trimToNull(request.getRemark()));
        }
        entity.setStorageStateEncrypted(cryptoService.encrypt(storageStateJson));
        entity.setStorageStateUpdatedAt(LocalDateTime.now());
        entity.setLastVerifiedAt(LocalDateTime.now());
        entity.setLastError(null);
        entity.setStatus("ACTIVE");
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }

        Alibaba1688AuthSession saved = repository.save(entity);
        return Alibaba1688AuthSessionDTO.WorkerSaveResponse.builder()
                .id(saved.getId())
                .sessionName(saved.getSessionName())
                .status(saved.getStatus())
                .build();
    }

    @Transactional
    public Alibaba1688AuthSessionDTO.ListItem workerUpdateStatus(Long id, Alibaba1688AuthSessionDTO.WorkerStatusRequest request) {
        Alibaba1688AuthSession entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + id));
        String status = trimToNull(request == null ? null : request.getStatus());
        if (StringUtils.hasText(status)) {
            entity.setStatus(status.toUpperCase());
        }
        entity.setLastError(trimToNull(request == null ? null : request.getLastError()));
        entity.setLastVerifiedAt(LocalDateTime.now());
        return toListItem(repository.save(entity));
    }

    private Alibaba1688AuthSession resolveForWorkerSave(Alibaba1688AuthSessionDTO.WorkerSaveRequest request) {
        if (request != null && request.getId() != null) {
            return repository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("1688 凭证不存在: " + request.getId()));
        }
        String sessionName = trimToNull(request == null ? null : request.getSessionName());
        if (StringUtils.hasText(sessionName)) {
            return repository.findBySessionNameIgnoreCase(sessionName).orElseGet(Alibaba1688AuthSession::new);
        }
        return new Alibaba1688AuthSession();
    }

    private void validateUniqueSessionName(String sessionName, Long selfId) {
        repository.findBySessionNameIgnoreCase(sessionName).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new IllegalArgumentException("会话名称已存在: " + sessionName);
            }
        });
    }

    private Alibaba1688AuthSessionDTO.ListItem toListItem(Alibaba1688AuthSession entity) {
        return Alibaba1688AuthSessionDTO.ListItem.builder()
                .id(entity.getId())
                .sessionName(entity.getSessionName())
                .accountNick(entity.getAccountNick())
                .memberId(entity.getMemberId())
                .homeUrl(entity.getHomeUrl())
                .remark(entity.getRemark())
                .enabled(entity.getEnabled())
                .status(entity.getStatus())
                .lastError(entity.getLastError())
                .hasStorageState(StringUtils.hasText(entity.getStorageStateEncrypted()))
                .storageStateUpdatedAt(entity.getStorageStateUpdatedAt())
                .lastVerifiedAt(entity.getLastVerifiedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Alibaba1688AuthSessionDTO.OptionItem toOptionItem(Alibaba1688AuthSession entity) {
        return Alibaba1688AuthSessionDTO.OptionItem.builder()
                .id(entity.getId())
                .sessionName(entity.getSessionName())
                .accountNick(entity.getAccountNick())
                .memberId(entity.getMemberId())
                .status(entity.getStatus())
                .build();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
