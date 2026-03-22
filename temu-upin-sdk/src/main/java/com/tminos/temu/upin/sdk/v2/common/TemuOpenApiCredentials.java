package com.tminos.temu.upin.sdk.v2.common;

/**
 * Runtime credentials for calling Temu OpenAPI.
 *
 * Notes:
 * - shopId is stored as String to avoid precision issues across languages.
 */
public class TemuOpenApiCredentials {

    /** TEMU shop id / mall id */
    private String shopId;

    /** Access token for the shop */
    private String accessToken;

    private String appKey;
    private String appSecret;

    public TemuOpenApiCredentials() {}

    public TemuOpenApiCredentials(String shopId, String accessToken, String appKey, String appSecret) {
        this.shopId = shopId;
        this.accessToken = accessToken;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
}
