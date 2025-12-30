package com.pura365.camera.model.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MQTT 灯泡配置消息
 * 用于解析 CODE 157 (设置响应) 和 CODE 158 (查询响应)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttBulbConfigMessage extends MqttBaseMessage {

    /** 摄像头序列号 */
    @JsonProperty("uid")
    private String uid;

    /** 状态: 0-失败, 1-成功 */
    @JsonProperty("status")
    private Integer status;

    /** 灯泡工作模式: 0-手动控制, 1-环境光自动控制, 2-定时控制 */
    @JsonProperty("detect")
    private Integer detect;

    /** 灯泡亮度: 0-100 */
    @JsonProperty("brightness")
    private Integer brightness;

    /** 灯泡开关状态: 0-关闭, 1-开启 */
    @JsonProperty("enable")
    private Integer enable;

    /** 定时一: 开启时间 hh:mm */
    @JsonProperty("time_on1")
    private String timeOn1;

    /** 定时一: 关闭时间 hh:mm */
    @JsonProperty("time_off1")
    private String timeOff1;

    /** 定时二: 开启时间 hh:mm */
    @JsonProperty("time_o2")
    private String timeOn2;

    /** 定时二: 关闭时间 hh:mm */
    @JsonProperty("time_off2")
    private String timeOff2;
}
