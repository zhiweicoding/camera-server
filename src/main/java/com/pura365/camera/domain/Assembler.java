package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 装机商实体，对应表 assembler
 */
@Data
@TableName("assembler")
public class Assembler {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 装机商代码(1位) */
    @TableField("assembler_code")
    private String assemblerCode;

    /** 装机商名称 */
    @TableField("assembler_name")
    private String assemblerName;

    /** 联系人 */
    @TableField("contact_person")
    private String contactPerson;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

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
