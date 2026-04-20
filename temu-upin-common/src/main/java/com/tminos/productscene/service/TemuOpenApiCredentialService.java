package com.tminos.productscene.service;

import com.tminos.productscene.entity.TemuSelfApp;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class TemuOpenApiCredentialService {

    private final TemuShopService shopService;

    public TemuOpenApiCredentialService(TemuShopService shopService) {
        this.shopService = shopService;
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getDefaultTemuOpenApiCredentialsOrThrow() {
        return toProductCredentials(shopService.getAnyEnabledShopOrThrow());
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getTemuOpenApiCredentialsByShopIdOrThrow(String shopId) {
        if (!StringUtils.hasText(shopId)) {
            return getDefaultTemuOpenApiCredentialsOrThrow();
        }
        return getTemuOpenApiCredentialsByExactShopIdOrThrow(shopId);
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getTemuOpenApiCredentialsByExactShopIdOrThrow(String shopId) {
        if (!StringUtils.hasText(shopId)) {
            throw new IllegalStateException("商品未绑定店铺，无法获取 TEMU 发布凭证");
        }
        return toProductCredentials(shopService.getEnabledShopByShopIdOrThrow(shopId));
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getTemuOpenApiCredentialsByShopRecordIdOrThrow(Long shopRecordId) {
        return toProductCredentials(shopService.getByIdOrThrow(shopRecordId));
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getOrderTemuOpenApiCredentialsByShopIdOrThrow(String shopId) {
        if (!StringUtils.hasText(shopId)) {
            return getDefaultOrderTemuOpenApiCredentialsOrThrow();
        }
        return getOrderTemuOpenApiCredentialsByExactShopIdOrThrow(shopId);
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getOrderTemuOpenApiCredentialsByExactShopIdOrThrow(String shopId) {
        if (!StringUtils.hasText(shopId)) {
            throw new IllegalStateException("商品未绑定店铺，无法获取 TEMU 订单凭证");
        }
        return toOrderCredentials(shopService.getEnabledShopByShopIdOrThrow(shopId));
    }

    @Transactional(readOnly = true)
    public TemuOpenApiCredentials getDefaultOrderTemuOpenApiCredentialsOrThrow() {
        return toOrderCredentials(shopService.getAnyEnabledShopOrThrow());
    }

    private TemuOpenApiCredentials toProductCredentials(TemuShop shop) {
        TemuSelfApp app = null;
        try {
            app = shop.getApp();
        } catch (Exception ignored) {
        }

        return buildCredentials(shop, app, shop == null ? null : shop.getToken(), "TEMU 店铺产品TOKEN为空");
    }

    private TemuOpenApiCredentials toOrderCredentials(TemuShop shop) {
        TemuSelfApp app = null;
        String token = null;
        try {
            app = shop.getOrderApp() != null ? shop.getOrderApp() : shop.getApp();
            token = StringUtils.hasText(shop.getOrderToken()) ? shop.getOrderToken() : shop.getToken();
        } catch (Exception ignored) {
        }
        return buildCredentials(shop, app, token, "TEMU 店铺订单TOKEN为空");
    }

    private TemuOpenApiCredentials buildCredentials(TemuShop shop,
                                                    TemuSelfApp app,
                                                    String token,
                                                    String tokenEmptyMessage) {
        String shopId = shop == null ? null : shop.getShopId();
        String appKey = app == null ? null : app.getAppKey();
        String appSecret = app == null ? null : app.getAppSecret();

        if (!StringUtils.hasText(shopId)) {
            throw new IllegalStateException("TEMU 店铺ID为空");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException(tokenEmptyMessage);
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
