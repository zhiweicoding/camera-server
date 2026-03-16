package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Dealer-level settlement record for a payment order.
 */
@Data
@TableName("payment_order_dealer_settlement")
public class PaymentOrderDealerSettlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private String orderId;

    @TableField("dealer_id")
    private Long dealerId;

    @TableField("settled_by")
    private Long settledBy;

    @TableField("settled_at")
    private Date settledAt;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}
