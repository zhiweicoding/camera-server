package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户反馈实体，对应表 feedback
 */
@Data
@TableName("feedback")
public class Feedback {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 反馈ID */
    @TableField("feedback_id")
    private String feedbackId;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 反馈内容 */
    @TableField("content")
    private String content;

    /** 联系方式 */
    @TableField("contact")
    private String contact;

    /** 图片(JSON数组) */
    @TableField("images")
    private String images;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
