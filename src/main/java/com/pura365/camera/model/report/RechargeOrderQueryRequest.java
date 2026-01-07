package com.pura365.camera.model.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 充值订单报表查询请求
 */
@Data
@Schema(description = "充值订单报表查询请求")
public class RechargeOrderQueryRequest {

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页数量", example = "20")
    private Integer size = 20;

    @Schema(description = "充值订单号")
    private String orderId;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "经销商代码")
    private String vendorCode;

    @Schema(description = "装机商代码")
    private String installerCode;

    @Schema(description = "业务员ID")
    private Long salesmanId;

    @Schema(description = "套餐ID")
    private String planId;

    @Schema(description = "套餐类型：motion-动态录像, fulltime-全天录像, traffic-4G流量")
    private String planType;

    @Schema(description = "支付方式：wechat-微信支付, paypal-PayPal, apple-苹果支付, google-谷歌支付")
    private String paymentMethod;

    @Schema(description = "支付币种：CNY-人民币, USD-美元")
    private String currency;

    @Schema(description = "订单状态：pending-待支付, paid-已支付, refunded-已退款")
    private String status;

    @Schema(description = "开始时间（下单时间）")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    @Schema(description = "结束时间（下单时间）")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    @Schema(description = "支付开始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date payStartDate;

    @Schema(description = "支付结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date payEndDate;
}
