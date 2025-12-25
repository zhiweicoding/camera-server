package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备列表项响应
 *
 * @author camera-server
 */
@Schema(description = "设备列表项")
@Data
public class DeviceListItemVO {

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
     * 设备状态：online/offline
     */
    @JsonProperty("status")
    @Schema(description = "设备状态", example = "online")
    private String status;

    /**
     * 是否有云存储
     */
    @JsonProperty("has_cloud_storage")
    @Schema(description = "是否有云存储")
    private Boolean hasCloudStorage;

    /**
     * 是否开启AI功能
     */
    @JsonProperty("ai_enabled")
    @Schema(description = "是否开启AI功能")
    private Boolean aiEnabled;

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
     * 最后在线时间
     */
    @JsonProperty("last_online_at")
    @Schema(description = "最后在线时间")
    private String lastOnlineAt;

    /**
     * WiFi信号强度
     */
    @Schema(description = "WiFi信号强度(RSSI)")
    @JsonProperty("wifi_rssi")
    private Integer wifiRssi;

    /**
     * 网络类型
     */
    @Schema(description = "网络类型: wifi/4g")
    @JsonProperty("network_type")
    private String networkType;



}
