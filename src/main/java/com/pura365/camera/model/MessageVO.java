package com.pura365.camera.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 消息响应 VO
 */
@Schema(description = "消息信息")
public class MessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "消息类型: motion-移动侦测, alert-报警")
    private String type;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "设备ID")
    @JsonProperty("device_id")
    private String deviceId;

    @Schema(description = "设备名称")
    @JsonProperty("device_name")
    private String deviceName;

    @Schema(description = "缩略图URL")
    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @Schema(description = "视频URL")
    @JsonProperty("video_url")
    private String videoUrl;

    @Schema(description = "是否已读")
    @JsonProperty("is_read")
    private Boolean isRead;

    @Schema(description = "创建时间")
    @JsonProperty("created_at")
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
