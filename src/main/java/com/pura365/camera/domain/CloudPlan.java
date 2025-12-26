package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 周期: month/year */
    @TableField("period")
    private String period;

    /** 套餐特性(JSON) */
    @TableField("features")
    private String features;

    /** 套餐类型: motion-动态录像, fulltime-全天录像, traffic-4G流量 */
    @TableField("type")
    private String type;

    /** 排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态: 1-启用, 0-禁用 */
    @TableField("status")
    private Integer status;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
