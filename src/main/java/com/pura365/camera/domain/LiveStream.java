package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 直播流实体，对应表 live_stream
 */
@Data
@TableName("live_stream")
public class LiveStream {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流ID */
    @TableField("stream_id")
    private String streamId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 协议: webrtc/hls/rtmp */
    @TableField("protocol")
    private String protocol;

    /** 画质: low/medium/high */
    @TableField("quality")
    private String quality;

    /** 信令服务器URL */
    @TableField("signaling_url")
    private String signalingUrl;

    /** ICE服务器配置(JSON) */
    @TableField("ice_servers")
    private String iceServers;

    /** 过期时间 */
    @TableField("expires_at")
    private Date expiresAt;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
