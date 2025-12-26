package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 云存储套餐列表响应（按类型分组）
 */
@Data
@Schema(description = "云存储套餐列表响应")
public class CloudPlansResponse {

    /**
     * 动态录像套餐列表
     */
    @JsonProperty("motion")
    @Schema(description = "动态录像套餐列表")
    private List<CloudPlanVO> motion;

    /**
     * 全天录像套餐列表
     */
    @JsonProperty("fulltime")
    @Schema(description = "全天录像套餐列表")
    private List<CloudPlanVO> fulltime;

    /**
     * 4G流量套餐列表
     */
    @JsonProperty("traffic")
    @Schema(description = "4G流量套餐列表")
    private List<CloudPlanVO> traffic;
}
