package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户操作日志实体，对应表 user_operation_log
 * 记录用户在APP中的操作行为
 */
@Data
@TableName("user_operation_log")
public class UserOperationLog {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 用户名/手机号（冗余存储，便于查询） */
    @TableField("username")
    private String username;

    /** 操作模块: device-设备管理, user-用户中心, cloud-云存储, payment-支付, share-分享, setting-设置 */
    @TableField("module")
    private String module;

    /** 操作类型: view-查看, add-添加, edit-修改, delete-删除, bind-绑定, unbind-解绑, share-分享, buy-购买 */
    @TableField("action")
    private String action;

    /** 操作描述 */
    @TableField("description")
    private String description;

    /** 操作目标ID（如设备ID、订单ID等） */
    @TableField("target_id")
    private String targetId;

    /** 操作目标类型: device-设备, order-订单, plan-套餐, share-分享 */
    @TableField("target_type")
    private String targetType;

    /** 请求参数 (JSON格式) */
    @TableField("request_params")
    private String requestParams;

    /** 响应结果 (JSON格式，可选) */
    @TableField("response_result")
    private String responseResult;

    /** 操作结果: success-成功, fail-失败 */
    @TableField("result")
    private String result;

    /** 错误信息（失败时记录） */
    @TableField("error_message")
    private String errorMessage;

    /** 客户端IP地址 */
    @TableField("ip_address")
    private String ipAddress;

    /** 客户端设备类型: android, ios, web */
    @TableField("device_type")
    private String deviceType;

    /** 客户端设备型号 */
    @TableField("device_model")
    private String deviceModel;

    /** App版本 */
    @TableField("app_version")
    private String appVersion;

    /** 操作系统版本 */
    @TableField("os_version")
    private String osVersion;

    /** 请求耗时（毫秒） */
    @TableField("cost_time")
    private Long costTime;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
