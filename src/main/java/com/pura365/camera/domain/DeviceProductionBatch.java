package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 设备生产批次实体，对应表 device_production_batch
 */
@Data
@TableName("device_production_batch")
public class DeviceProductionBatch {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号 */
    @TableField("batch_no")
    private String batchNo;

    /** 网络+镜头配置(第1-2位) */
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

    /** 预留位(第8位) */
    @TableField("reserved")
    private String reserved;

    /** 生产数量 */
    @TableField("quantity")
    private Integer quantity;

    /** 起始序列号 */
    @TableField("start_serial")
    private Integer startSerial;

    /** 结束序列号 */
    @TableField("end_serial")
    private Integer endSerial;

    /** 状态: pending/producing/completed */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 创建人 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;

    /**
     * 生成设备ID前缀 (前8位)
     */
    public String getDeviceIdPrefix() {
        return networkLens + deviceForm + specialReq + assemblerCode + vendorCode + reserved;
    }
}
