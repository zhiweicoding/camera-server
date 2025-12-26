package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 设备基础信息响应（用于添加/更新设备返回）
 *
 * @author camera-server
 */
@Schema(description = "设备基础信息")
public class DeviceVO {

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
