package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备消息实体，对应表 device_message
 */
@Data
@TableName("device_message")
public class DeviceMessage {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 消息主题 */
    @TableField("topic")
    private String topic;

    /** 消息标题 */
    @TableField("title")
    private String title;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 消息类型: event/alert/ai */
    @TableField("message_type")
    private String messageType;

    /** 严重等级: 0-普通 1-警告 2-严重 */
    @TableField("severity")
    private Integer severity;

    /** 是否已读: 0-未读 1-已读 */
    @TableField("is_read")
    private Integer isRead;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
