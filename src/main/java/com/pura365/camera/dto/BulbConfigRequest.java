package com.pura365.camera.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * 灯泡配置请求 DTO
 */
@Data
@Schema(description = "灯泡配置请求")
public class BulbConfigRequest {

    @NotNull(message = "工作模式不能为空")
    @Min(value = 0, message = "工作模式值无效")
    @Max(value = 2, message = "工作模式值无效")
    @Schema(description = "灯泡工作模式: 0-手动控制, 1-环境光自动控制, 2-定时控制", required = true, example = "0")
    private Integer detect;

    @Min(value = 0, message = "亮度值范围 0-100")
    @Max(value = 100, message = "亮度值范围 0-100")
    @Schema(description = "灯泡亮度 0-100", example = "50")
    private Integer brightness;

    @Min(value = 0, message = "开关状态值无效")
    @Max(value = 1, message = "开关状态值无效")
    @Schema(description = "手动模式开关: 0-关闭, 1-开启", example = "1")
    private Integer enable;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "时间格式应为 hh:mm")
    @Schema(description = "定时一: 开启时间 (格式 hh:mm)", example = "08:00")
    private String timeOn1;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "时间格式应为 hh:mm")
    @Schema(description = "定时一: 关闭时间 (格式 hh:mm)", example = "18:00")
    private String timeOff1;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "时间格式应为 hh:mm")
    @Schema(description = "定时二: 开启时间 (格式 hh:mm)", example = "20:00")
    private String timeOn2;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "时间格式应为 hh:mm")
    @Schema(description = "定时二: 关闭时间 (格式 hh:mm)", example = "23:00")
    private String timeOff2;
}
