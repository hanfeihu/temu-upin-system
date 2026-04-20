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
import java.util.Objects;

@Service
public class TemuShopService {

    public static final int DEFAULT_SITE_ID = 100;
    public static final String DEFAULT_WAREHOUSE_ID = "WH-03304781516934009";
    public static final int DEFAULT_SKU_DEFAULT_STOCK = 1000;
    public static final int DEFAULT_SKU_MAX_STOCK = 10842;
    public static final String DEFAULT_ORIGIN_REGION1_SHORT_NAME = "CN";
    public static final long DEFAULT_ORIGIN_REGION2_ID = 43000000000016L;
    public static final String DEFAULT_FREIGHT_TEMPLATE_ID = "HFT-14851213328261424009";
    public static final int DEFAULT_SHIPMENT_LIMIT_SECOND = 777600;

    public record PublishConfig(
            String shopId,
            String shopName,
            Integer siteId,
            String warehouseId,
            Integer skuDefaultStock,
            Integer skuMaxStock,
            String originRegion1ShortName,
            Long originRegion2Id,
            String freightTemplateId,
            Integer shipmentLimitSecond
    ) {
    }

    private final TemuShopRepository repo;
    private final TemuSelfAppRepository appRepo;

    public TemuShopService(TemuShopRepository repo, TemuSelfAppRepository appRepo) {
        this.repo = repo;
        this.appRepo = appRepo;
    }

    @Transactional(readOnly = true)
    public List<TemuShopDTO.View> list(Boolean enabled) {
        List<TemuShop> rows;
        if (enabled == null) {
            rows = repo.findAll();
            rows.sort((a, b) -> Long.compare(
                    b == null || b.getId() == null ? Long.MIN_VALUE : b.getId(),
                    a == null || a.getId() == null ? Long.MIN_VALUE : a.getId()
            ));
        } else {
            rows = repo.findByEnabledOrderByIdDesc(enabled);
        }
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
        String orderToken = trim(req.getOrderToken());
        String dianxiaomiCookie = trim(req.getDianxiaomiCookie());
        Long appId = req.getAppId();
        Long orderAppId = req.getOrderAppId();

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
        e.setOrderToken(orderToken);
        e.setDianxiaomiCookie(dianxiaomiCookie);
        if (orderAppId != null) {
            TemuSelfApp orderApp = appRepo.findById(orderAppId).orElseThrow(() -> new EntityNotFoundException("order app not found"));
            e.setOrderApp(orderApp);
        }
        applyPublishConfig(
                e,
                req.getSiteId(),
                req.getWarehouseId(),
                req.getDefaultStock(),
                req.getMaxStock(),
                req.getOriginRegion1ShortName(),
                req.getOriginRegion2Id(),
                req.getFreightTemplateId(),
                req.getShipmentLimitSecond()
        );
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

        String orderToken = trim(req.getOrderToken());
        if (StringUtils.hasText(orderToken)) {
            e.setOrderToken(orderToken);
        }
        String dianxiaomiCookie = trim(req.getDianxiaomiCookie());
        if (StringUtils.hasText(dianxiaomiCookie)) {
            e.setDianxiaomiCookie(dianxiaomiCookie);
        }

        Long appId = req.getAppId();
        if (appId == null) throw new IllegalArgumentException("应用ID不能为空");
        TemuSelfApp app = appRepo.findById(appId).orElseThrow(() -> new EntityNotFoundException("app not found"));
        e.setApp(app);

        if (req.getOrderAppId() != null) {
            TemuSelfApp orderApp = appRepo.findById(req.getOrderAppId()).orElseThrow(() -> new EntityNotFoundException("order app not found"));
            e.setOrderApp(orderApp);
        } else {
            e.setOrderApp(null);
        }

        applyPublishConfig(
                e,
                req.getSiteId(),
                req.getWarehouseId(),
                req.getDefaultStock(),
                req.getMaxStock(),
                req.getOriginRegion1ShortName(),
                req.getOriginRegion2Id(),
                req.getFreightTemplateId(),
                req.getShipmentLimitSecond()
        );
        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TemuShop getByIdOrThrow(Long id) {
        if (id == null) {
            throw new IllegalStateException("TEMU 店铺记录ID为空");
        }
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("shop not found"));
    }

    @Transactional(readOnly = true)
    public TemuShop getEnabledShopByShopIdOrThrow(String shopId) {
        if (!StringUtils.hasText(shopId)) {
            throw new IllegalStateException("TEMU 店铺ID为空");
        }
        TemuShop shop = repo.findByShopId(shopId.trim())
                .orElseThrow(() -> new IllegalStateException("未找到对应的 TEMU 店铺: " + shopId));
        if (!Boolean.TRUE.equals(shop.getEnabled())) {
            throw new IllegalStateException("TEMU 店铺未启用: " + shopId);
        }
        return shop;
    }

    @Transactional(readOnly = true)
    public TemuShop getEnabledShopByIdOrThrow(Long id) {
        if (id == null) {
            throw new IllegalStateException("TEMU 店铺记录ID为空");
        }
        TemuShop shop = repo.findById(id)
                .orElseThrow(() -> new IllegalStateException("未找到对应的 TEMU 店铺记录: " + id));
        if (!Boolean.TRUE.equals(shop.getEnabled())) {
            throw new IllegalStateException("TEMU 店铺未启用: " + id);
        }
        return shop;
    }

    @Transactional(readOnly = true)
    public TemuShop getAnyEnabledShopOrThrow() {
        List<TemuShop> shops = repo.findByEnabledOrderByIdDesc(true);
        if (shops == null || shops.isEmpty()) {
            throw new IllegalStateException("未找到启用中的 TEMU 店铺，请先在【TEMU 店铺管理】中创建并启用店铺");
        }
        return shops.get(0);
    }

    @Transactional(readOnly = true)
    public List<TemuShop> listEnabledShops() {
        return repo.findByEnabledOrderByIdDesc(true);
    }

    @Transactional(readOnly = true)
    public PublishConfig getPublishConfigByShopIdOrThrow(String shopId) {
        return toPublishConfig(getEnabledShopByShopIdOrThrow(shopId));
    }

    @Transactional(readOnly = true)
    public PublishConfig getAnyEnabledPublishConfigOrThrow() {
        return toPublishConfig(getAnyEnabledShopOrThrow());
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }

    private static String mask(String token) {
        if (!StringUtils.hasText(token)) return "";
        String s = token.trim();
        if (s.length() <= 10) return "********";
        return s.substring(0, 3) + "********" + s.substring(s.length() - 3);
    }

    private static String maskCookie(String cookie) {
        return StringUtils.hasText(trim(cookie)) ? "已配置" : "";
    }

    private static void applyPublishConfig(TemuShop shop,
                                           Integer siteId,
                                           String warehouseId,
                                           Integer defaultStock,
                                           Integer maxStock,
                                           String originRegion1ShortName,
                                           Long originRegion2Id,
                                           String freightTemplateId,
                                           Integer shipmentLimitSecond) {
        if (shop == null) {
            return;
        }
        int normalizedDefaultStock = positiveOrDefault(defaultStock, DEFAULT_SKU_DEFAULT_STOCK);
        shop.setSiteId(positiveOrDefault(siteId, DEFAULT_SITE_ID));
        shop.setWarehouseId(firstNonBlank(warehouseId, DEFAULT_WAREHOUSE_ID));
        shop.setSkuDefaultStock(normalizedDefaultStock);
        shop.setSkuMaxStock(Math.max(positiveOrDefault(maxStock, DEFAULT_SKU_MAX_STOCK), normalizedDefaultStock));
        shop.setOriginRegion1ShortName(firstNonBlank(originRegion1ShortName, DEFAULT_ORIGIN_REGION1_SHORT_NAME));
        shop.setOriginRegion2Id(positiveOrDefault(originRegion2Id, DEFAULT_ORIGIN_REGION2_ID));
        shop.setFreightTemplateId(firstNonBlank(freightTemplateId, DEFAULT_FREIGHT_TEMPLATE_ID));
        shop.setShipmentLimitSecond(positiveOrDefault(shipmentLimitSecond, DEFAULT_SHIPMENT_LIMIT_SECOND));
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static long positiveOrDefault(Long value, long fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static String firstNonBlank(String value, String fallback) {
        String text = trim(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private static PublishConfig toPublishConfig(TemuShop shop) {
        Objects.requireNonNull(shop, "shop");
        return new PublishConfig(
                trim(shop.getShopId()),
                firstNonBlank(shop.getShopName(), trim(shop.getShopId())),
                positiveOrDefault(shop.getSiteId(), DEFAULT_SITE_ID),
                firstNonBlank(shop.getWarehouseId(), DEFAULT_WAREHOUSE_ID),
                positiveOrDefault(shop.getSkuDefaultStock(), DEFAULT_SKU_DEFAULT_STOCK),
                Math.max(
                        positiveOrDefault(shop.getSkuMaxStock(), DEFAULT_SKU_MAX_STOCK),
                        positiveOrDefault(shop.getSkuDefaultStock(), DEFAULT_SKU_DEFAULT_STOCK)
                ),
                firstNonBlank(shop.getOriginRegion1ShortName(), DEFAULT_ORIGIN_REGION1_SHORT_NAME),
                positiveOrDefault(shop.getOriginRegion2Id(), DEFAULT_ORIGIN_REGION2_ID),
                firstNonBlank(shop.getFreightTemplateId(), DEFAULT_FREIGHT_TEMPLATE_ID),
                positiveOrDefault(shop.getShipmentLimitSecond(), DEFAULT_SHIPMENT_LIMIT_SECOND)
        );
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static TemuShopDTO.View toView(TemuShop e) {
        TemuShopDTO.View v = new TemuShopDTO.View();
        v.setId(e.getId());
        v.setEnabled(e.getEnabled());
        v.setShopName(e.getShopName());
        v.setShopId(e.getShopId());
        v.setProductTokenMasked(mask(e.getToken()));
        v.setOrderTokenMasked(mask(e.getOrderToken()));
        v.setDianxiaomiCookieMasked(maskCookie(e.getDianxiaomiCookie()));
        try {
            if (e.getApp() != null) {
                v.setProductAppId(e.getApp().getId());
                v.setProductAppName(e.getApp().getAppName());
            }
            if (e.getOrderApp() != null) {
                v.setOrderAppId(e.getOrderApp().getId());
                v.setOrderAppName(e.getOrderApp().getAppName());
            }
        } catch (Exception ignored) {
        }
        PublishConfig config = toPublishConfig(e);
        v.setSiteId(config.siteId());
        v.setWarehouseId(config.warehouseId());
        v.setDefaultStock(config.skuDefaultStock());
        v.setMaxStock(config.skuMaxStock());
        v.setOriginRegion1ShortName(config.originRegion1ShortName());
        v.setOriginRegion2Id(config.originRegion2Id());
        v.setFreightTemplateId(config.freightTemplateId());
        v.setShipmentLimitSecond(config.shipmentLimitSecond());
        if (e.getCreatedAt() != null) v.setCreatedAt(e.getCreatedAt().format(FMT));
        if (e.getUpdatedAt() != null) v.setUpdatedAt(e.getUpdatedAt().format(FMT));
        return v;
    }
}
