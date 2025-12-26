package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 微信支付记录实体，对应表 payment_wechat
 */
@Data
@TableName("payment_wechat")
public class PaymentWechat {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    @TableField("order_id")
    private String orderId;

    /** 微信预支付ID */
    @TableField("prepay_id")
    private String prepayId;

    /** 原始响应数据 */
    @TableField("raw_response")
    private String rawResponse;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
