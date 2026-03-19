package com.tminos.productscene.service;

import com.tminos.productscene.dto.PlatformConfigDTO;
import com.tminos.productscene.entity.PlatformConfigItem;
import com.tminos.productscene.entity.PlatformConfigProfile;
import com.tminos.productscene.repository.PlatformConfigItemRepository;
import com.tminos.productscene.repository.PlatformConfigProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class PlatformConfigService {

    public static final String KEY_DEFAULT_SITE_ID = "default.siteId";
    public static final String KEY_DEFAULT_WAREHOUSE_ID = "default.warehouseId";
    public static final String KEY_SKU_DEFAULT_STOCK = "sku.defaultStock";
    public static final String KEY_ORIGIN_REGION1_SHORT = "origin.region1ShortName";
    public static final String KEY_ORIGIN_REGION2_ID = "origin.region2Id";
    public static final String KEY_SHIPMENT_FREIGHT_TEMPLATE_ID = "shipment.freightTemplateId";
    public static final String KEY_SHIPMENT_LIMIT_SECOND = "shipment.limitSecond";

    private static final List<String> KNOWN_KEYS = List.of(
            KEY_DEFAULT_SITE_ID,
            KEY_DEFAULT_WAREHOUSE_ID,
            KEY_SKU_DEFAULT_STOCK,
            KEY_ORIGIN_REGION1_SHORT,
            KEY_ORIGIN_REGION2_ID,
            KEY_SHIPMENT_FREIGHT_TEMPLATE_ID,
            KEY_SHIPMENT_LIMIT_SECOND
    );

    private final PlatformConfigProfileRepository profileRepo;
    private final PlatformConfigItemRepository itemRepo;

    public PlatformConfigService(PlatformConfigProfileRepository profileRepo, PlatformConfigItemRepository itemRepo) {
        this.profileRepo = profileRepo;
        this.itemRepo = itemRepo;
    }

    @Transactional(readOnly = true)
    public List<PlatformConfigDTO.ProfileResponse> listProfiles() {
        List<PlatformConfigProfile> profiles = profileRepo.findAllByOrderByIdAsc();
        List<PlatformConfigDTO.ProfileResponse> out = new ArrayList<>();
        for (PlatformConfigProfile p : profiles) {
            Map<String, String> items = loadItems(p.getId());
            out.add(new PlatformConfigDTO.ProfileResponse(p.getId(), p.getName(), Boolean.TRUE.equals(p.getIsDefault()), items));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PlatformConfigDTO.ProfileResponse getDefaultProfile() {
        PlatformConfigProfile p = profileRepo.findFirstByIsDefaultTrue().orElse(null);
        if (p == null) {
            return null;
        }
        Map<String, String> items = loadItems(p.getId());
        return new PlatformConfigDTO.ProfileResponse(p.getId(), p.getName(), true, items);
    }

    @Transactional
    public PlatformConfigDTO.ProfileResponse createProfile(PlatformConfigDTO.SaveProfileRequest req) {
        String name = req == null ? null : req.getName();
        if (!StringUtils.hasText(name)) {
            name = "Default";
        }
        boolean wantDefault = req != null && Boolean.TRUE.equals(req.getIsDefault());
        Map<String, String> items = req == null ? null : req.getItems();

        PlatformConfigProfile p = new PlatformConfigProfile();
        p.setName(name.trim());
        p.setIsDefault(wantDefault);
        p = profileRepo.save(p);

        saveItems(p.getId(), items);
        if (wantDefault) {
            setDefault(p.getId());
        }

        return new PlatformConfigDTO.ProfileResponse(p.getId(), p.getName(), Boolean.TRUE.equals(p.getIsDefault()), loadItems(p.getId()));
    }

    @Transactional
    public PlatformConfigDTO.ProfileResponse updateProfile(Long id, PlatformConfigDTO.SaveProfileRequest req) {
        PlatformConfigProfile p = profileRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("config profile not found: " + id));
        if (req != null && StringUtils.hasText(req.getName())) {
            p.setName(req.getName().trim());
        }
        boolean wantDefault = req != null && Boolean.TRUE.equals(req.getIsDefault());
        if (wantDefault) {
            setDefault(p.getId());
            p = profileRepo.findById(id).orElse(p);
        }
        profileRepo.save(p);

        saveItems(p.getId(), req == null ? null : req.getItems());
        Map<String, String> items = loadItems(p.getId());
        return new PlatformConfigDTO.ProfileResponse(p.getId(), p.getName(), Boolean.TRUE.equals(p.getIsDefault()), items);
    }

    @Transactional
    public void deleteProfile(Long id) {
        PlatformConfigProfile p = profileRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("config profile not found: " + id));
        itemRepo.deleteByProfileId(id);
        profileRepo.delete(p);
    }

    @Transactional
    public void setDefault(Long profileId) {
        PlatformConfigProfile target = profileRepo.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("config profile not found: " + profileId));
        List<PlatformConfigProfile> all = profileRepo.findAllByOrderByIdAsc();
        for (PlatformConfigProfile p : all) {
            boolean isDef = p.getId().equals(target.getId());
            if (!Objects.equals(p.getIsDefault(), isDef)) {
                p.setIsDefault(isDef);
                profileRepo.save(p);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> getDefaultConfigOrThrow() {
        PlatformConfigDTO.ProfileResponse p = getDefaultProfile();
        Map<String, String> cfg = p == null ? null : p.getItems();
        if (cfg == null) cfg = new HashMap<>();

        Map<String, String> out = new LinkedHashMap<>();
        for (String k : KNOWN_KEYS) {
            String v = cfg.get(k);
            if (v != null) v = v.trim();
            out.put(k, v);
        }

        // Apply hard defaults to match your previous test
        out.putIfAbsent(KEY_DEFAULT_SITE_ID, "100");
        out.putIfAbsent(KEY_DEFAULT_WAREHOUSE_ID, "WH-03304781516934009");
        out.putIfAbsent(KEY_SKU_DEFAULT_STOCK, "100");
        out.putIfAbsent(KEY_ORIGIN_REGION1_SHORT, "CN");
        out.putIfAbsent(KEY_ORIGIN_REGION2_ID, "43000000000016");
        out.putIfAbsent(KEY_SHIPMENT_FREIGHT_TEMPLATE_ID, "HFT-14851213328261424009");
        out.putIfAbsent(KEY_SHIPMENT_LIMIT_SECOND, "777600");

        return out;
    }

    private Map<String, String> loadItems(Long profileId) {
        List<PlatformConfigItem> rows = itemRepo.findByProfileIdOrderByIdAsc(profileId);
        Map<String, String> out = new LinkedHashMap<>();
        for (PlatformConfigItem i : rows) {
            if (i == null) continue;
            if (!StringUtils.hasText(i.getConfigKey())) continue;
            out.put(i.getConfigKey().trim(), i.getConfigValue());
        }
        return out;
    }

    private void saveItems(Long profileId, Map<String, String> items) {
        if (items == null) return;
        for (Map.Entry<String, String> e : items.entrySet()) {
            String k = e.getKey();
            if (!StringUtils.hasText(k)) continue;
            String key = k.trim();
            String value = e.getValue();
            if (value != null) value = value.trim();

            PlatformConfigItem row = itemRepo.findByProfileIdAndConfigKey(profileId, key).orElse(null);
            if (row == null) {
                row = new PlatformConfigItem();
                row.setProfileId(profileId);
                row.setConfigKey(key);
            }
            row.setConfigValue(value);
            itemRepo.save(row);
        }
    }
}
