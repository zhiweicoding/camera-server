package com.pura365.camera.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class SendMsgRequest {
    /** 设备ID */
    @JsonAlias({"device_id", "deviceId", "uid", "uide"})
    private String id;
    
    @JsonAlias({"event", "type", "channel"})
    @NotBlank(message = "通知主题不能为空")
    private String topic;
    
    @JsonAlias({"subject", "notification_title"})
    @NotBlank(message = "通知标题不能为空")
    private String title;
    
    @JsonAlias({"message", "content", "body", "notification_content"})
    @NotBlank(message = "通知内容不能为空")
    private String msg;
    
    @JsonAlias({"timestamp", "time", "ts"})
    @NotNull(message = "时间戳不能为空")
    private Long exp;
    
    /** 图片URL */
    @JsonAlias({"pic_url", "thumbnail", "thumbnail_url", "image_url"})
    private String picurl;
    
    /** 视频URL */
    @JsonAlias({"video_url", "url", "record_url"})
    private String videourl;

    
    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    
    public Long getExp() { return exp; }
    public void setExp(Long exp) { this.exp = exp; }
    
    public String getPicurl() { return picurl; }
    public void setPicurl(String picurl) { this.picurl = picurl; }
    
    public String getVideourl() { return videourl; }
    public void setVideourl(String videourl) { this.videourl = videourl; }
    
    @Override
    public String toString() {
        return "SendMsgRequest{" +
                "id='" + id + '\'' +
                ", topic='" + topic + '\'' +
                ", title='" + title + '\'' +
                ", msg='" + msg + '\'' +
                ", exp=" + exp +
                ", picurl='" + picurl + '\'' +
                ", videourl='" + videourl + '\'' +
                '}';
    }
}
