package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 摄像头视频上传成功通知请求
 */
@Data
public class VideoUploadNotifyRequest {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    private String deviceId;

    /**
     * 视频在云存储中的key（路径）
     * 例如：videos/CAM123/20231206/1702012345678.mp4
     */
    @JsonProperty("key")
    private String key;

    /**
     * 视频标题
     * 例如：运动检测录像、定时录像
     */
    @JsonProperty("title")
    private String title;

    /**
     * 视频类型
     * motion - 运动检测触发
     * scheduled - 定时录像
     * manual - 手动录制
     * alarm - 报警录像
     */
    @JsonProperty("type")
    private String type;

    /**
     * 视频时长（秒）
     */
    @JsonProperty("duration")
    private Integer duration;

    /**
     * 缩略图URL（可选）
     */
    @JsonProperty("thumbnail")
    private String thumbnail;

    /**
     * 视频录制开始时间戳（秒）
     */
    @JsonProperty("start_time")
    private Long startTime;

    /**
     * 视频文件大小（字节）
     */
    @JsonProperty("file_size")
    private Long fileSize;
}
