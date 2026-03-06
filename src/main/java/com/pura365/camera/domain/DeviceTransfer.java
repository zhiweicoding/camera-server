package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.TransferStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 设备转让记录实体，对应表 device_transfer
 * 记录经销商之间的设备转让操作
 */
@Data
@TableName("device_transfer")
public class DeviceTransfer {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 转让单号 */
    @TableField("transfer_no")
    private String transferNo;

    /** 转出经销商ID（NULL表示装机商直接分配） */
    @TableField("from_vendor_id")
    private Long fromVendorId;

    /** 转出经销商代码 */
    @TableField("from_vendor_code")
    private String fromVendorCode;

    /** 转入经销商ID */
    @TableField("to_vendor_id")
    private Long toVendorId;

    /** 转入经销商代码 */
    @TableField("to_vendor_code")
    private String toVendorCode;

    /**
     * 分润比例（转入经销商从总经销商分润池中抽取的百分比）
     * 例如：总经销商分润池=30，转入经销商rate=10%
     *      则 转入经销商实际所得=30×10%=3，转出经销商实际所得=30×(100%-10%)=27
     */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 转让设备数量 */
    @TableField("device_count")
    private Integer deviceCount;

    /** 设备ID列表（JSON数组） */
    @TableField("device_ids")
    private String deviceIds;

    /** 所属装机商ID */
    @TableField("installer_id")
    private Long installerId;

    /** 所属装机商代码 */
    @TableField("installer_code")
    private String installerCode;

    /** 状态：PENDING-待确认, COMPLETED-已完成, CANCELLED-已取消 */
    @TableField("status")
    private TransferStatus status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
