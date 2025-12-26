package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 云存储视频实体，对应表 cloud_video
 */
@Data
@TableName("cloud_video")
public class CloudVideo {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 视频ID */
    @TableField("video_id")
    private String videoId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 视频类型 */
    @TableField("type")
    private String type;

    /** 视频标题 */
    @TableField("title")
    private String title;

    /** 缩略图 */
    @TableField("thumbnail")
    private String thumbnail;

    /** 视频URL */
    @TableField("video_url")
    private String videoUrl;

    /** 时长(秒) */
    @TableField("duration")
    private Integer duration;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
