package com.pura365.camera.model.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CODE 11/139: 设备信息响应
 */
public class MqttDeviceInfoMessage extends MqttBaseMessage {
    @JsonProperty("uid")
    private String uid;
    
    @JsonProperty("wifiname")
    private String wifiname;
    
    @JsonProperty("wifirssi")
    private Integer wifirssi;
    
    @JsonProperty("ver")
    private String ver;

    @JsonProperty("iccid")
    private String iccid;
    
    @JsonProperty("sdstate")
    private Integer sdstate; // 0: 无TF卡, 1: 有TF卡
    
    @JsonProperty("sdcap")
    private Long sdcap;
    
    @JsonProperty("sdblock")
    private Long sdblock;
    
    @JsonProperty("sdfree")
    private Long sdfree;
    
    @JsonProperty("rotate")
    private Integer rotate; // 0: 没有旋转, 1: 旋转180度
    
    @JsonProperty("lightled")
    private Integer lightled; // 0: 关闭, 1: 开启
    
    @JsonProperty("whiteled")
    private Integer whiteled; // 0: 禁用白光, 1: 启用白光
    
    @JsonProperty("bulbs_en")
    private Integer bulbsEn; // 0: 不支持灯泡, 1: 支持灯泡
    
    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    
    public String getWifiname() { return wifiname; }
    public void setWifiname(String wifiname) { this.wifiname = wifiname; }
    
    public Integer getWifirssi() { return wifirssi; }
    public void setWifirssi(Integer wifirssi) { this.wifirssi = wifirssi; }
    
    public String getVer() { return ver; }
    public void setVer(String ver) { this.ver = ver; }

    public String getIccid() { return iccid; }
    public void setIccid(String iccid) { this.iccid = iccid; }
    
    public Integer getSdstate() { return sdstate; }
    public void setSdstate(Integer sdstate) { this.sdstate = sdstate; }
    
    public Long getSdcap() { return sdcap; }
    public void setSdcap(Long sdcap) { this.sdcap = sdcap; }
    
    public Long getSdblock() { return sdblock; }
    public void setSdblock(Long sdblock) { this.sdblock = sdblock; }
    
    public Long getSdfree() { return sdfree; }
    public void setSdfree(Long sdfree) { this.sdfree = sdfree; }
    
    public Integer getRotate() { return rotate; }
    public void setRotate(Integer rotate) { this.rotate = rotate; }
    
    public Integer getLightled() { return lightled; }
    public void setLightled(Integer lightled) { this.lightled = lightled; }
    
    public Integer getWhiteled() { return whiteled; }
    public void setWhiteled(Integer whiteled) { this.whiteled = whiteled; }
    
    public Integer getBulbsEn() { return bulbsEn; }
    public void setBulbsEn(Integer bulbsEn) { this.bulbsEn = bulbsEn; }
}
