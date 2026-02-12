package com.pura365.camera.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * 创建生产批次请求参数
 */
public class CreateBatchRequest {

    /**
     * 网络+镜头配置 (第1-2位)
     * 可选值: A1/A2/A3/B1/B2/B3/C1/C2/R1
     */
    private String networkLens;

    /**
     * 机型描述（可选），用于保存到 sys_dict.name
     */
    @JsonAlias("deviceModelDesc")
    private String networkLensDesc;

    /**
     * 设备形态 (第3位)
     * 可选值: 1-常电卡片机, 2-常电摇头机, 3-PIR电池卡片机, 4-PIR电池摇头机, 5-AOV电池卡片机
     */
    private String deviceForm;

    /**
     * 特殊要求 (第4位)
     * 可选值: 0-无, 1-需要白光灯控制按键, 2-拐杖产品, 3-鸟笼产品
     */
    private String specialReq;

    /**
     * 装机商代码 (第5位)
     * 前端参数名: installerIdCode
     */
    @JsonAlias("installerIdCode")
    private String assemblerCode;

    /**
     * 销售商/经销商代码 (第6-7位)
     * 前端参数名: dealerIdCode
     */
    @JsonAlias("dealerIdCode")
    private String vendorCode;

    /**
     * 预留位 (第8位)，默认为 "0"
     */
    private String reserved = "0";

    /**
     * 合同编号
     */
    private String contractNo;

    /**
     * 是否开启广告
     */
    private Boolean enableAd;

    /**
     * 生产数量
     */
    private Integer quantity;

    /**
     * 起始序列号（可选），不填则自动衔接上一批次
     */
    private Integer startSerial;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 装机商分润比例(%)
     */
    private java.math.BigDecimal installerCommissionRate;

    /**
     * 经销商分润比例(%)
     */
    private java.math.BigDecimal dealerCommissionRate;

    // Getters and Setters

    public String getNetworkLens() {
        return networkLens;
    }

    public void setNetworkLens(String networkLens) {
        this.networkLens = networkLens;
    }

    public String getNetworkLensDesc() {
        return networkLensDesc;
    }

    public void setNetworkLensDesc(String networkLensDesc) {
        this.networkLensDesc = networkLensDesc;
    }

    public String getDeviceForm() {
        return deviceForm;
    }

    public void setDeviceForm(String deviceForm) {
        this.deviceForm = deviceForm;
    }

    public String getSpecialReq() {
        return specialReq;
    }

    public void setSpecialReq(String specialReq) {
        this.specialReq = specialReq;
    }

    public String getAssemblerCode() {
        return assemblerCode;
    }

    public void setAssemblerCode(String assemblerCode) {
        this.assemblerCode = assemblerCode;
    }

    public String getVendorCode() {
        return vendorCode;
    }

    public void setVendorCode(String vendorCode) {
        this.vendorCode = vendorCode;
    }

    public String getReserved() {
        return reserved;
    }

    /**
     * 支持前端传入字符串或数字类型的 reserved
     */
    @JsonSetter("reserved")
    public void setReserved(Object reserved) {
        if (reserved != null) {
            this.reserved = String.valueOf(reserved);
        }
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public Boolean getEnableAd() {
        return enableAd;
    }

    public void setEnableAd(Boolean enableAd) {
        this.enableAd = enableAd;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getStartSerial() {
        return startSerial;
    }

    public void setStartSerial(Integer startSerial) {
        this.startSerial = startSerial;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public java.math.BigDecimal getInstallerCommissionRate() {
        return installerCommissionRate;
    }

    public void setInstallerCommissionRate(java.math.BigDecimal installerCommissionRate) {
        this.installerCommissionRate = installerCommissionRate;
    }

    public java.math.BigDecimal getDealerCommissionRate() {
        return dealerCommissionRate;
    }

    public void setDealerCommissionRate(java.math.BigDecimal dealerCommissionRate) {
        this.dealerCommissionRate = dealerCommissionRate;
    }
}
