package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * App消息实体，对应表 app_message
 */
@Data
@TableName("app_message")
public class AppMessage {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 消息类型 */
    @TableField("type")
    private String type;

    /** 消息标题 */
    @TableField("title")
    private String title;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 缩略图URL */
    @TableField("thumbnail_url")
    private String thumbnailUrl;

    /** 视频URL */
    @TableField("video_url")
    private String videoUrl;

    /** 是否已读: 0-未读 1-已读 */
    @TableField("is_read")
    private Integer isRead;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
