package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuSelfAppDTO;
import com.tminos.productscene.entity.TemuSelfApp;
import com.tminos.productscene.repository.TemuSelfAppRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TemuSelfAppService {

    private final TemuSelfAppRepository repo;

    public TemuSelfAppService(TemuSelfAppRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TemuSelfAppDTO.View> list(Boolean enabled) {
        Boolean en = enabled == null ? Boolean.TRUE : enabled;
        List<TemuSelfApp> rows = repo.findByEnabledOrderByIdDesc(en);
        List<TemuSelfAppDTO.View> out = new ArrayList<>();
        for (TemuSelfApp r : rows) out.add(toView(r));
        return out;
    }

    @Transactional
    public TemuSelfAppDTO.View create(TemuSelfAppDTO.CreateRequest req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        String name = trim(req.getAppName());
        String key = trim(req.getAppKey());
        String secret = trim(req.getAppSecret());
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("应用名称不能为空");
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("App Key 不能为空");
        if (!StringUtils.hasText(secret)) throw new IllegalArgumentException("App Secret 不能为空");

        if (repo.existsByAppName(name)) throw new IllegalArgumentException("应用名称已存在");
        if (repo.existsByAppKey(key)) throw new IllegalArgumentException("App Key 已存在");

        TemuSelfApp e = new TemuSelfApp();
        e.setAppName(name);
        e.setAppKey(key);
        e.setAppSecret(secret);
        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());
        return toView(repo.save(e));
    }

    @Transactional
    public TemuSelfAppDTO.View update(Long id, TemuSelfAppDTO.UpdateRequest req) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (req == null) throw new IllegalArgumentException("request is required");

        TemuSelfApp e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("app not found"));

        String name = trim(req.getAppName());
        String key = trim(req.getAppKey());
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("应用名称不能为空");
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("App Key 不能为空");

        if (repo.existsByAppNameAndIdNot(name, id)) throw new IllegalArgumentException("应用名称已存在");
        if (repo.existsByAppKeyAndIdNot(key, id)) throw new IllegalArgumentException("App Key 已存在");

        e.setAppName(name);
        e.setAppKey(key);

        String secret = trim(req.getAppSecret());
        if (StringUtils.hasText(secret)) {
            e.setAppSecret(secret);
        }

        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());

        return toView(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        repo.deleteById(id);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String mask(String secret) {
        if (!StringUtils.hasText(secret)) return "";
        String s = secret.trim();
        if (s.length() <= 8) return "********";
        return s.substring(0, 2) + "********" + s.substring(s.length() - 2);
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static TemuSelfAppDTO.View toView(TemuSelfApp e) {
        TemuSelfAppDTO.View v = new TemuSelfAppDTO.View();
        v.setId(e.getId());
        v.setEnabled(e.getEnabled());
        v.setAppName(e.getAppName());
        v.setAppKey(e.getAppKey());
        v.setAppSecretMasked(mask(e.getAppSecret()));
        if (e.getCreatedAt() != null) v.setCreatedAt(e.getCreatedAt().format(FMT));
        if (e.getUpdatedAt() != null) v.setUpdatedAt(e.getUpdatedAt().format(FMT));
        return v;
    }
}
