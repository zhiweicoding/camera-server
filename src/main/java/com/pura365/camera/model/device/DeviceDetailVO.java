package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 设备详情响应
 *
 * @author camera-server
 */
@Schema(description = "设备详情信息")
public class DeviceDetailVO {

    /**
     * 设备ID（序列号）
     */
    @JsonProperty("id")
    @Schema(description = "设备ID")
    private String id;

    /**
     * 设备名称
     */
    @JsonProperty("name")
    @Schema(description = "设备名称")
    private String name;

    /**
     * 设备型号
     */
    @JsonProperty("model")
    @Schema(description = "设备型号")
    private String model;

    /**
     * MAC地址
     */
    @JsonProperty("mac")
    @Schema(description = "MAC地址")
    private String mac;

    /**
     * 固件版本
     */
    @JsonProperty("firmware_version")
    @Schema(description = "固件版本")
    private String firmwareVersion;

    /**
     * 设备状态：online/offline
     */
    @JsonProperty("status")
    @Schema(description = "设备状态: online/offline")
    private String status;

    /**
     * 是否有云存储
     */
    @JsonProperty("has_cloud_storage")
    @Schema(description = "是否有云存储")
    private Boolean hasCloudStorage;

    /**
     * 云存储到期时间（ISO8601格式）
     */
    @JsonProperty("cloud_expire_at")
    @Schema(description = "云存储到期时间")
    private String cloudExpireAt;

    /**
     * 缩略图URL
     */
    @JsonProperty("thumbnail_url")
    @Schema(description = "缩略图URL")
    private String thumbnailUrl;

    /**
     * WiFi SSID
     */
    @JsonProperty("wifi_ssid")
    @Schema(description = "WiFi名称")
    private String wifiSsid;

    /**
     * WiFi信号强度(RSSI)
     */
    @JsonProperty("wifi_rssi")
    @Schema(description = "WiFi信号强度(RSSI)")
    private Integer wifiRssi;

    /**
     * SD卡信息
     */
    @JsonProperty("sd_card")
    @Schema(description = "SD卡信息")
    private SdCardInfoVO sdCard;

    /**
     * 画面旋转: 0-正常, 1-旋转180度
     */
    @JsonProperty("rotate")
    @Schema(description = "画面旋转: 0-正常, 1-旋转180度")
    private Integer rotate;

    /**
     * 指示灯状态
     */
    @JsonProperty("light_led")
    @Schema(description = "指示灯状态")
    private Integer lightLed;

    /**
     * 白光灯状态
     */
    @JsonProperty("white_led")
    @Schema(description = "白光灯状态")
    private Integer whiteLed;

    /**
     * 最后在线时间
     */
    @JsonProperty("last_online_at")
    @Schema(description = "最后在线时间")
    private String lastOnlineAt;

    /**
     * 最后心跳时间
     */
    @JsonProperty("last_heartbeat_at")
    @Schema(description = "最后心跳时间")
    private String lastHeartbeatAt;

    /**
     * 是否已领取7天免费云录像
     */
    @JsonProperty("free_cloud_claimed")
    @Schema(description = "是否已领取7天免费云录像")
    private Boolean freeCloudClaimed;

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getHasCloudStorage() {
        return hasCloudStorage;
    }

    public void setHasCloudStorage(Boolean hasCloudStorage) {
        this.hasCloudStorage = hasCloudStorage;
    }

    public String getCloudExpireAt() {
        return cloudExpireAt;
    }

    public void setCloudExpireAt(String cloudExpireAt) {
        this.cloudExpireAt = cloudExpireAt;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getWifiSsid() {
        return wifiSsid;
    }

    public void setWifiSsid(String wifiSsid) {
        this.wifiSsid = wifiSsid;
    }

    public Integer getWifiRssi() {
        return wifiRssi;
    }

    public void setWifiRssi(Integer wifiRssi) {
        this.wifiRssi = wifiRssi;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(String lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Boolean getFreeCloudClaimed() {
        return freeCloudClaimed;
    }

    public void setFreeCloudClaimed(Boolean freeCloudClaimed) {
        this.freeCloudClaimed = freeCloudClaimed;
    }

    public SdCardInfoVO getSdCard() {
        return sdCard;
    }

    public void setSdCard(SdCardInfoVO sdCard) {
        this.sdCard = sdCard;
    }

    public Integer getRotate() {
        return rotate;
    }

    public void setRotate(Integer rotate) {
        this.rotate = rotate;
    }

    public Integer getLightLed() {
        return lightLed;
    }

    public void setLightLed(Integer lightLed) {
        this.lightLed = lightLed;
    }

    public Integer getWhiteLed() {
        return whiteLed;
    }

    public void setWhiteLed(Integer whiteLed) {
        this.whiteLed = whiteLed;
    }

    public String getLastOnlineAt() {
        return lastOnlineAt;
    }

    public void setLastOnlineAt(String lastOnlineAt) {
        this.lastOnlineAt = lastOnlineAt;
    }
}
