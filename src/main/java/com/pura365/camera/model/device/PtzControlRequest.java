package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

/**
 * 云台控制请求参数
 *
 * @author camera-server
 */
@Schema(description = "云台控制请求")
@ToString
public class PtzControlRequest {

    /**
     * 云台方向：up/down/left/right/stop
     */
    @NotBlank(message = "方向不能为空")
    @JsonProperty("direction")
    @Schema(description = "云台方向", required = true, example = "up", allowableValues = {"up", "down", "left", "right", "stop"})
    private String direction;

    /**
     * 云台速度（可选）
     */
    @JsonProperty("speed")
    @Schema(description = "云台速度", example = "5")
    private Integer speed;

    // Getters and Setters

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
    }
}
