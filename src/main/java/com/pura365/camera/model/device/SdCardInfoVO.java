package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SD卡信息响应
 *
 * @author camera-server
 */
@Schema(description = "SD卡信息")
public class SdCardInfoVO {

    /**
     * SD卡状态: 0-无卡, 1-有卡
     */
    @JsonProperty("state")
    @Schema(description = "SD卡状态: 0-无卡, 1-有卡")
    private Integer state;

    /**
     * 总容量（字节）
     */
    @JsonProperty("total")
    @Schema(description = "总容量（字节）")
    private Long total;

    /**
     * 已用容量（字节）
     */
    @JsonProperty("used")
    @Schema(description = "已用容量（字节）")
    private Long used;

    /**
     * 可用容量（字节）
     */
    @JsonProperty("available")
    @Schema(description = "可用容量（字节）")
    private Long available;

    public SdCardInfoVO() {
        this.state = 0;
        this.total = 0L;
        this.used = 0L;
        this.available = 0L;
    }

    public SdCardInfoVO(Integer state, Long total, Long used, Long available) {
        this.state = state;
        this.total = total;
        this.used = used;
        this.available = available;
    }

    // Getters and Setters

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getUsed() {
        return used;
    }

    public void setUsed(Long used) {
        this.used = used;
    }

    public Long getAvailable() {
        return available;
    }

    public void setAvailable(Long available) {
        this.available = available;
    }
}
