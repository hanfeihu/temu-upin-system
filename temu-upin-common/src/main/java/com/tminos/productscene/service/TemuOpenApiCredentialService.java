package com.tminos.productscene.service;

import com.tminos.productscene.entity.TemuSelfApp;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuShopRepository;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TemuOpenApiCredentialService {

    private final TemuShopRepository shopRepository;
    private final PlatformConfigService platformConfigService;

    public TemuOpenApiCredentialService(TemuShopRepository shopRepository,
                                        PlatformConfigService platformConfigService) {
        this.shopRepository = shopRepository;
        this.platformConfigService = platformConfigService;
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getDefaultTemuOpenApiCredentialsOrThrow() {
        TemuShop shop = resolveConfiguredShop();
        if (shop == null) {
            List<TemuShop> shops = shopRepository.findByEnabledOrderByIdDesc(true);
            if (shops == null || shops.isEmpty()) {
                throw new IllegalStateException("未找到可用的 TEMU 店铺配置，请先在【平台配置】绑定默认店铺，或先在【TEMU店铺】中创建并启用一个店铺");
            }
            shop = shops.get(0);
        }
        if (shop == null) {
            throw new IllegalStateException("TEMU 店铺配置为空");
        }

        return toCredentials(shop);
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getTemuOpenApiCredentialsByShopIdOrThrow(String shopId) {
        if (!StringUtils.hasText(shopId)) {
            return getDefaultTemuOpenApiCredentialsOrThrow();
        }
        TemuShop shop = shopRepository.findByShopId(shopId.trim())
                .orElseThrow(() -> new IllegalStateException("未找到对应的 TEMU 店铺: " + shopId));
        if (!Boolean.TRUE.equals(shop.getEnabled())) {
            throw new IllegalStateException("TEMU 店铺未启用: " + shopId);
        }
        return toCredentials(shop);
    }

    private TemuShop resolveConfiguredShop() {
        Map<String, String> cfg = platformConfigService.getDefaultConfigOrThrow();
        String shopRefId = cfg.get(PlatformConfigService.KEY_TEMU_SHOP_REF_ID);
        if (!StringUtils.hasText(shopRefId)) {
            return null;
        }

        Long shopDbId;
        try {
            shopDbId = Long.parseLong(shopRefId.trim());
        } catch (Exception e) {
            throw new IllegalStateException("平台配置中的默认 TEMU 店铺无效，请重新选择店铺");
        }

        TemuShop shop = shopRepository.findById(shopDbId)
                .orElseThrow(() -> new IllegalStateException("平台配置关联的 TEMU 店铺不存在，请重新选择店铺"));
        if (!Boolean.TRUE.equals(shop.getEnabled())) {
            throw new IllegalStateException("平台配置关联的 TEMU 店铺未启用，请先启用该店铺或重新绑定默认店铺");
        }
        return shop;
    }

    private TemuOpenApiCredentials toCredentials(TemuShop shop) {
        TemuSelfApp app = null;
        try {
            app = shop.getApp();
        } catch (Exception ignored) {
        }

        String shopId = shop.getShopId();
        String token = shop.getToken();
        String appKey = app == null ? null : app.getAppKey();
        String appSecret = app == null ? null : app.getAppSecret();

        if (!StringUtils.hasText(shopId)) {
            throw new IllegalStateException("TEMU 店铺ID为空");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("TEMU 店铺TOKEN为空");
        }
        if (!StringUtils.hasText(appKey)) {
            throw new IllegalStateException("TEMU 应用 appKey 为空");
        }
        if (!StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("TEMU 应用 appSecret 为空");
        }
        String normalizedAppKey = Objects.toString(appKey, "").trim();
        String normalizedAppSecret = Objects.toString(appSecret, "").trim();

        TemuOpenApiCredentials creds = new TemuOpenApiCredentials();
        creds.setShopId(shopId.trim());
        creds.setAccessToken(token.trim());
        creds.setAppKey(normalizedAppKey);
        creds.setAppSecret(normalizedAppSecret);
        return creds;
    }
}
