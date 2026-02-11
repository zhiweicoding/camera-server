package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.EnableStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 云存储套餐实体，对应表 cloud_plan
 */
@Data
@TableName("cloud_plan")
public class CloudPlan {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 套餐ID */
    @TableField("plan_id")
    private String planId;

    /** 套餐名称 */
    @TableField("name")
    private String name;

    /** 套餐描述 */
    @TableField("description")
    private String description;

    /** 存储天数 */
    @TableField("storage_days")
    private Integer storageDays;

    /** 价格 */
    @TableField("price")
    private BigDecimal price;

    /** 原价 */
    @TableField("original_price")
    private BigDecimal originalPrice;

    /** 套餐成本 */
    @TableField("plan_cost")
    private BigDecimal planCost;

    /** 周期: month-月付, year-年付 */
    @TableField("period")
    private String period;

    /** 周期月数: 1/3/12 */
    @TableField("period_num")
    private Integer periodNum;

    /** 套餐特性(JSON) */
    @TableField("features")
    private String features;

    /** 套餐类型: motion-动态录像, fulltime-全天录像, traffic-4G流量 等 */
    @TableField("type")
    private String type;

    /** 机型代码(关联network_lens字典) */
    @TableField("device_model")
    private String deviceModel;

    /** 流量(GB), 仅4G流量类型使用 */
    @TableField("traffic_gb")
    private Integer trafficGb;

    /** 语言设置: zh/en等 */
    @TableField("language")
    private String language;

    /** 是否自动续费: 0-否 1-是 */
    @TableField("auto_renew")
    private Integer autoRenew;

    /** 排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态: ENABLED-启用, DISABLED-禁用 */
    @TableField("status")
    private EnableStatus status;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
