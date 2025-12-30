package com.pura365.camera.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 灯泡配置响应 DTO
 */
@Data
@Schema(description = "灯泡配置响应")
public class BulbConfigResponse {

    @Schema(description = "灯泡工作模式: 0-手动控制, 1-环境光自动控制, 2-定时控制")
    private Integer detect;

    @Schema(description = "灯泡亮度 0-100")
    private Integer brightness;

    @Schema(description = "灯泡开关状态: 0-关闭, 1-开启")
    private Integer enable;

    @Schema(description = "定时一: 开启时间 (格式 hh:mm)")
    private String timeOn1;

    @Schema(description = "定时一: 关闭时间 (格式 hh:mm)")
    private String timeOff1;

    @Schema(description = "定时二: 开启时间 (格式 hh:mm)")
    private String timeOn2;

    @Schema(description = "定时二: 关闭时间 (格式 hh:mm)")
    private String timeOff2;
}
