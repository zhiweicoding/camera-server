package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 生产设备实体，对应表 manufactured_device
 */
@Data
@TableName("manufactured_device")
public class ManufacturedDevice {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 完整设备ID(16位) */
    @TableField("device_id")
    private String deviceId;

    /** 关联批次ID */
    @TableField("batch_id")
    private Long batchId;

    /** 网络+镜头(第1-2位) */
    @TableField("network_lens")
    private String networkLens;

    /** 设备形态(第3位) */
    @TableField("device_form")
    private String deviceForm;

    /** 特殊要求(第4位) */
    @TableField("special_req")
    private String specialReq;

    /** 装机商代码(第5位) */
    @TableField("assembler_code")
    private String assemblerCode;

    /** 销售商代码(第6-7位) */
    @TableField("vendor_code")
    private String vendorCode;

    /** 业务员ID */
    @TableField("salesman_id")
    private Long salesmanId;

    /** 序列号(第9-16位) */
    @TableField("serial_no")
    private String serialNo;

    /** MAC地址 */
    @TableField("mac_address")
    private String macAddress;

    /** 状态: manufactured/activated/bound */
    @TableField("status")
    private String status;

    /** 生产时间 */
    @TableField("manufactured_at")
    private Date manufacturedAt;

    /** 激活时间 */
    @TableField("activated_at")
    private Date activatedAt;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
