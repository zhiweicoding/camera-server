package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 设备录像记录实体，对应表 device_record
 */
@Data
@TableName("device_record")
public class DeviceRecord {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 录像ID */
    @TableField("record_id")
    private String recordId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 录像状态 */
    @TableField("status")
    private String status;

    /** 视频URL */
    @TableField("video_url")
    private String videoUrl;

    /** 时长(秒) */
    @TableField("duration")
    private Integer duration;

    /** 文件大小(字节) */
    @TableField("size")
    private Long size;

    /** 开始时间 */
    @TableField("started_at")
    private Date startedAt;

    /** 结束时间 */
    @TableField("ended_at")
    private Date endedAt;
}
