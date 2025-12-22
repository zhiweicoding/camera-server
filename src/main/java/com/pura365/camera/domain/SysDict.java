package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 系统字典实体，对应表 sys_dict
 */
@Data
@TableName("sys_dict")
public class SysDict {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类: network_lens/device_form/special_req/reserved/assembler_code */
    @TableField("category")
    private String category;

    /** 字典代码 */
    @TableField("code")
    private String code;

    /** 显示名称 */
    @TableField("name")
    private String name;

    /** 排序 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态: 0-禁用 1-启用 */
    @TableField("status")
    private Integer status;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
