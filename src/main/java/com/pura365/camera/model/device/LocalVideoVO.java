package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 本地录像响应
 *
 * @author camera-server
 */
@Schema(description = "本地录像信息")
public class LocalVideoVO {

    /**
     * 视频ID
     */
    @JsonProperty("id")
    @Schema(description = "视频ID")
    private String id;

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID")
    private String deviceId;

    /**
     * 视频类型
     */
    @JsonProperty("type")
    @Schema(description = "视频类型")
    private String type;

    /**
     * 视频标题
     */
    @JsonProperty("title")
    @Schema(description = "视频标题")
    private String title;

    /**
     * 缩略图URL
     */
    @JsonProperty("thumbnail_url")
    @Schema(description = "缩略图URL")
    private String thumbnailUrl;

    /**
     * 视频URL
     */
    @JsonProperty("video_url")
    @Schema(description = "视频URL")
    private String videoUrl;

    /**
     * 视频时长（秒）
     */
    @JsonProperty("duration")
    @Schema(description = "视频时长（秒）")
    private Integer duration;

    /**
     * 视频大小（字节）
     */
    @JsonProperty("size")
    @Schema(description = "视频大小（字节）")
    private Long size;

    /**
     * 创建时间（ISO8601格式）
     */
    @JsonProperty("created_at")
    @Schema(description = "创建时间")
    private String createdAt;

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
