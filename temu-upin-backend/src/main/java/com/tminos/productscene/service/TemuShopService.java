package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuShopDTO;
import com.tminos.productscene.entity.TemuSelfApp;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuSelfAppRepository;
import com.tminos.productscene.repository.TemuShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TemuShopService {

    private final TemuShopRepository repo;
    private final TemuSelfAppRepository appRepo;

    public TemuShopService(TemuShopRepository repo, TemuSelfAppRepository appRepo) {
        this.repo = repo;
        this.appRepo = appRepo;
    }

    @Transactional(readOnly = true)
    public List<TemuShopDTO.View> list(Boolean enabled) {
        Boolean en = enabled == null ? Boolean.TRUE : enabled;
        List<TemuShop> rows = repo.findByEnabledOrderByIdDesc(en);
        List<TemuShopDTO.View> out = new ArrayList<>();
        for (TemuShop r : rows) out.add(toView(r));
        return out;
    }

    @Transactional
    public TemuShopDTO.View create(TemuShopDTO.CreateRequest req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        String name = trim(req.getShopName());
        String shopId = trim(req.getShopId());
        String token = trim(req.getToken());
        Long appId = req.getAppId();

        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("店铺名称不能为空");
        if (!StringUtils.hasText(shopId)) throw new IllegalArgumentException("店铺ID不能为空");
        if (!StringUtils.hasText(token)) throw new IllegalArgumentException("TOKEN 不能为空");
        if (appId == null) throw new IllegalArgumentException("应用ID不能为空");

        if (repo.existsByShopId(shopId)) throw new IllegalArgumentException("店铺ID已存在");

        TemuSelfApp app = appRepo.findById(appId).orElseThrow(() -> new EntityNotFoundException("app not found"));

        TemuShop e = new TemuShop();
        e.setShopName(name);
        e.setShopId(shopId);
        e.setToken(token);
        e.setApp(app);
        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());
        return toView(repo.save(e));
    }

    @Transactional
    public TemuShopDTO.View update(Long id, TemuShopDTO.UpdateRequest req) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (req == null) throw new IllegalArgumentException("request is required");

        TemuShop e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("shop not found"));

        String name = trim(req.getShopName());
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("店铺名称不能为空");
        e.setShopName(name);

        String shopId = trim(req.getShopId());
        if (!StringUtils.hasText(shopId)) throw new IllegalArgumentException("店铺ID不能为空");
        if (repo.existsByShopIdAndIdNot(shopId, id)) throw new IllegalArgumentException("店铺ID已存在");
        e.setShopId(shopId);

        String token = trim(req.getToken());
        if (StringUtils.hasText(token)) e.setToken(token);

        Long appId = req.getAppId();
        if (appId == null) throw new IllegalArgumentException("应用ID不能为空");
        TemuSelfApp app = appRepo.findById(appId).orElseThrow(() -> new EntityNotFoundException("app not found"));
        e.setApp(app);

        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        repo.deleteById(id);
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }

    private static String mask(String token) {
        if (!StringUtils.hasText(token)) return "";
        String s = token.trim();
        if (s.length() <= 10) return "********";
        return s.substring(0, 3) + "********" + s.substring(s.length() - 3);
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static TemuShopDTO.View toView(TemuShop e) {
        TemuShopDTO.View v = new TemuShopDTO.View();
        v.setId(e.getId());
        v.setEnabled(e.getEnabled());
        v.setShopName(e.getShopName());
        v.setShopId(e.getShopId());
        v.setTokenMasked(mask(e.getToken()));
        try {
            if (e.getApp() != null) {
                v.setAppId(e.getApp().getId());
                v.setAppName(e.getApp().getAppName());
            }
        } catch (Exception ignored) {
        }
        if (e.getCreatedAt() != null) v.setCreatedAt(e.getCreatedAt().format(FMT));
        if (e.getUpdatedAt() != null) v.setUpdatedAt(e.getUpdatedAt().format(FMT));
        return v;
    }
}
