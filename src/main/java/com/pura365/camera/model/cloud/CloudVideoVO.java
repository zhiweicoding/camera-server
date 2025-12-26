package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 云存储视频信息
 */
@Data
@Schema(description = "云存储视频信息")
public class CloudVideoVO {

    /**
     * 视频ID
     */
    @JsonProperty("video_id")
    @Schema(description = "视频ID", example = "video_20231213_120000")
    private String videoId;

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", example = "DEVICE123456")
    private String deviceId;

    /**
     * 视频类型
     */
    @JsonProperty("type")
    @Schema(description = "视频类型: motion-动态录像, scheduled-定时录像", example = "motion")
    private String type;

    /**
     * 视频标题
     */
    @JsonProperty("title")
    @Schema(description = "视频标题", example = "动态录像 2023-12-13 12:00:00")
    private String title;

    /**
     * 缩略图URL
     */
    @JsonProperty("thumbnail")
    @Schema(description = "缩略图URL", example = "https://s3.example.com/thumbnails/xxx.jpg")
    private String thumbnail;

    /**
     * 视频播放URL
     */
    @JsonProperty("video_url")
    @Schema(description = "视频播放URL", example = "https://s3.example.com/videos/xxx.mp4")
    private String videoUrl;

    /**
     * 视频时长（秒）
     */
    @JsonProperty("duration")
    @Schema(description = "视频时长（秒）", example = "60")
    private Integer duration;

    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    @Schema(description = "创建时间（ISO8601格式）", example = "2023-12-13T12:00:00Z")
    private String createdAt;
}
