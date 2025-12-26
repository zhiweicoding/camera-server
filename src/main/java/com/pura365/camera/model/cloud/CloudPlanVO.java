package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 云存储套餐信息
 */
@Data
@Schema(description = "云存储套餐信息")
public class CloudPlanVO {

    /**
     * 套餐ID
     */
    @JsonProperty("id")
    @Schema(description = "套餐ID", example = "motion-year-7d")
    private String id;

    /**
     * 套餐名称
     */
    @JsonProperty("name")
    @Schema(description = "套餐名称", example = "年卡【7天循环】")
    private String name;

    /**
     * 套餐描述
     */
    @JsonProperty("description")
    @Schema(description = "套餐描述", example = "所有数据，循环存储7天")
    private String description;

    /**
     * 存储天数
     */
    @JsonProperty("storage_days")
    @Schema(description = "存储天数", example = "7")
    private Integer storageDays;

    /**
     * 现价
     */
    @JsonProperty("price")
    @Schema(description = "现价（元）", example = "98.00")
    private BigDecimal price;

    /**
     * 原价
     */
    @JsonProperty("original_price")
    @Schema(description = "原价（元）", example = "198.00")
    private BigDecimal originalPrice;

    /**
     * 计费周期
     */
    @JsonProperty("period")
    @Schema(description = "计费周期: year-年, quarter-季度, month-月", example = "year")
    private String period;

    /**
     * 套餐特性列表
     */
    @JsonProperty("features")
    @Schema(description = "套餐特性列表", example = "[\"动态录像\", \"7天循环存储\", \"年卡套餐\"]")
    private List<String> features;

    /**
     * 套餐类型
     */
    @JsonProperty("type")
    @Schema(description = "套餐类型: motion-动态录像, fulltime-全天录像, traffic-4G流量", example = "motion")
    private String type;

    /**
     * 排序序号
     */
    @JsonProperty("sort_order")
    @Schema(description = "排序序号", example = "1")
    private Integer sortOrder;
}
