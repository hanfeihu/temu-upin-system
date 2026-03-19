package com.tminos.productscene.service;

import com.tminos.productscene.entity.TemuImageMeta;
import com.tminos.productscene.repository.TemuImageMetaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TemuImageMetaService {

    private final TemuImageMetaRepository repo;

    public TemuImageMetaService(TemuImageMetaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Map<String, TemuImageMeta> getByUrls(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) return Collections.emptyMap();
        Set<String> uniq = new LinkedHashSet<>();
        for (String u : urls) {
            if (!StringUtils.hasText(u)) continue;
            uniq.add(u.trim());
        }
        if (uniq.isEmpty()) return Collections.emptyMap();
        List<TemuImageMeta> list = repo.findByUrlIn(uniq);
        Map<String, TemuImageMeta> out = new HashMap<>();
        for (TemuImageMeta m : list) {
            if (m != null && StringUtils.hasText(m.getUrl())) {
                out.put(m.getUrl().trim(), m);
            }
        }
        return out;
    }

    @Transactional
    public void upsert(String url, Integer width, Integer height) {
        if (!StringUtils.hasText(url)) return;
        String u = url.trim();
        String host = null;
        boolean isKwcdn = false;
        try {
            URI uri = URI.create(u);
            host = uri.getHost();
            isKwcdn = host != null && "img.kwcdn.com".equalsIgnoreCase(host);
        } catch (Exception ignored) {
        }

        TemuImageMeta meta = repo.findByUrl(u).orElseGet(() -> {
            TemuImageMeta m = new TemuImageMeta();
            m.setUrl(u);
            return m;
        });
        meta.setHost(host);
        meta.setKwcdn(isKwcdn);
        meta.setCheckedAt(LocalDateTime.now());
        if (width != null && width > 0) meta.setWidth(width);
        if (height != null && height > 0) meta.setHeight(height);
        repo.save(meta);
    }
}
