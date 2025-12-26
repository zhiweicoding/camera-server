package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * App版本实体，对应表 app_version
 */
@Data
@TableName("app_version")
public class AppVersion {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台: android/ios */
    @TableField("platform")
    private String platform;

    /** 最新版本号 */
    @TableField("latest_version")
    private String latestVersion;

    /** 最低支持版本号 */
    @TableField("min_version")
    private String minVersion;

    /** 下载地址 */
    @TableField("download_url")
    private String downloadUrl;

    /** 版本更新说明 */
    @TableField("release_notes")
    private String releaseNotes;

    /** 是否强制更新: 0-否 1-是 */
    @TableField("force_update")
    private Integer forceUpdate;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
