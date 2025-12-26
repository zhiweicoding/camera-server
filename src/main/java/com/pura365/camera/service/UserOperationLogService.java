package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.domain.UserOperationLog;
import com.pura365.camera.repository.UserOperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Map;

/**
 * 用户操作日志服务
 * 用于记录和查询用户操作日志
 */
@Service
public class UserOperationLogService {

    private static final Logger log = LoggerFactory.getLogger(UserOperationLogService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserOperationLogRepository userOperationLogRepository;

    /**
     * 异步记录用户操作日志
     * 使用异步方式避免影响主业务性能
     */
    @Async
    public void logAsync(UserOperationLog operationLog) {
        try {
            if (operationLog.getCreatedAt() == null) {
                operationLog.setCreatedAt(new Date());
            }
            userOperationLogRepository.insert(operationLog);
            log.debug("用户操作日志记录成功 - userId: {}, module: {}, action: {}", 
                operationLog.getUserId(), operationLog.getModule(), operationLog.getAction());
        } catch (Exception e) {
            log.error("记录用户操作日志失败", e);
        }
    }

    /**
     * 同步记录用户操作日志
     */
    public void log(UserOperationLog operationLog) {
        try {
            if (operationLog.getCreatedAt() == null) {
                operationLog.setCreatedAt(new Date());
            }
            userOperationLogRepository.insert(operationLog);
        } catch (Exception e) {
            log.error("记录用户操作日志失败", e);
        }
    }

    /**
     * 快速记录操作日志（简化版）
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param module 操作模块
     * @param action 操作类型
     * @param description 操作描述
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param result 操作结果 (success/fail)
     * @param request HTTP请求对象，用于获取IP等信息
     */
    public void log(Long userId, String username, String module, String action,
                    String description, String targetType, String targetId,
                    String result, HttpServletRequest request) {
        UserOperationLog operationLog = new UserOperationLog();
        operationLog.setUserId(userId);
        operationLog.setUsername(username);
        operationLog.setModule(module);
        operationLog.setAction(action);
        operationLog.setDescription(description);
        operationLog.setTargetType(targetType);
        operationLog.setTargetId(targetId);
        operationLog.setResult(result);
        operationLog.setCreatedAt(new Date());

        if (request != null) {
            operationLog.setIpAddress(getClientIp(request));
            operationLog.setDeviceType(parseDeviceType(request.getHeader("User-Agent")));
        }

        logAsync(operationLog);
    }

    /**
     * 快速记录成功的操作日志
     */
    public void logSuccess(Long userId, String username, String module, String action,
                           String description, String targetType, String targetId,
                           HttpServletRequest request) {
        log(userId, username, module, action, description, targetType, targetId, "success", request);
    }

    /**
     * 快速记录失败的操作日志
     */
    public void logFail(Long userId, String username, String module, String action,
                        String description, String targetType, String targetId,
                        String errorMessage, HttpServletRequest request) {
        UserOperationLog operationLog = new UserOperationLog();
        operationLog.setUserId(userId);
        operationLog.setUsername(username);
        operationLog.setModule(module);
        operationLog.setAction(action);
        operationLog.setDescription(description);
        operationLog.setTargetType(targetType);
        operationLog.setTargetId(targetId);
        operationLog.setResult("fail");
        operationLog.setErrorMessage(errorMessage);
        operationLog.setCreatedAt(new Date());

        if (request != null) {
            operationLog.setIpAddress(getClientIp(request));
            operationLog.setDeviceType(parseDeviceType(request.getHeader("User-Agent")));
        }

        logAsync(operationLog);
    }

    /**
     * 构建操作日志对象（Builder模式）
     */
    public OperationLogBuilder builder() {
        return new OperationLogBuilder(this);
    }

    /**
     * 分页查询用户操作日志
     * 
     * @param userId 用户ID（可选）
     * @param module 操作模块（可选）
     * @param action 操作类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    public Page<UserOperationLog> queryLogs(Long userId, String module, String action,
                                            Date startTime, Date endTime, int page, int size) {
        QueryWrapper<UserOperationLog> wrapper = new QueryWrapper<>();

        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (module != null && !module.isEmpty()) {
            wrapper.eq("module", module);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.eq("action", action);
        }
        if (startTime != null) {
            wrapper.ge("created_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_at", endTime);
        }

        wrapper.orderByDesc("created_at");

        Page<UserOperationLog> pageParam = new Page<>(page, size);
        return userOperationLogRepository.selectPage(pageParam, wrapper);
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 根据User-Agent解析设备类型
     */
    private String parseDeviceType(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("android")) {
            return "android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "ios";
        } else if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) {
            return "web";
        }
        return "unknown";
    }

    /**
     * 将对象转换为JSON字符串
     */
    public String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象转JSON失败", e);
            return obj.toString();
        }
    }

    /**
     * 操作日志构建器
     */
    public static class OperationLogBuilder {
        private final UserOperationLogService service;
        private final UserOperationLog log;

        public OperationLogBuilder(UserOperationLogService service) {
            this.service = service;
            this.log = new UserOperationLog();
        }

        public OperationLogBuilder userId(Long userId) {
            log.setUserId(userId);
            return this;
        }

        public OperationLogBuilder username(String username) {
            log.setUsername(username);
            return this;
        }

        public OperationLogBuilder module(String module) {
            log.setModule(module);
            return this;
        }

        public OperationLogBuilder action(String action) {
            log.setAction(action);
            return this;
        }

        public OperationLogBuilder description(String description) {
            log.setDescription(description);
            return this;
        }

        public OperationLogBuilder targetId(String targetId) {
            log.setTargetId(targetId);
            return this;
        }

        public OperationLogBuilder targetType(String targetType) {
            log.setTargetType(targetType);
            return this;
        }

        public OperationLogBuilder requestParams(String requestParams) {
            log.setRequestParams(requestParams);
            return this;
        }

        public OperationLogBuilder requestParams(Object obj) {
            log.setRequestParams(service.toJson(obj));
            return this;
        }

        public OperationLogBuilder responseResult(String responseResult) {
            log.setResponseResult(responseResult);
            return this;
        }

        public OperationLogBuilder result(String result) {
            log.setResult(result);
            return this;
        }

        public OperationLogBuilder success() {
            log.setResult("success");
            return this;
        }

        public OperationLogBuilder fail(String errorMessage) {
            log.setResult("fail");
            log.setErrorMessage(errorMessage);
            return this;
        }

        public OperationLogBuilder ipAddress(String ipAddress) {
            log.setIpAddress(ipAddress);
            return this;
        }

        public OperationLogBuilder deviceType(String deviceType) {
            log.setDeviceType(deviceType);
            return this;
        }

        public OperationLogBuilder deviceModel(String deviceModel) {
            log.setDeviceModel(deviceModel);
            return this;
        }

        public OperationLogBuilder appVersion(String appVersion) {
            log.setAppVersion(appVersion);
            return this;
        }

        public OperationLogBuilder osVersion(String osVersion) {
            log.setOsVersion(osVersion);
            return this;
        }

        public OperationLogBuilder costTime(Long costTime) {
            log.setCostTime(costTime);
            return this;
        }

        public OperationLogBuilder request(HttpServletRequest request) {
            if (request != null) {
                log.setIpAddress(service.getClientIp(request));
                log.setDeviceType(service.parseDeviceType(request.getHeader("User-Agent")));
            }
            return this;
        }

        /**
         * 异步保存日志
         */
        public void saveAsync() {
            service.logAsync(log);
        }

        /**
         * 同步保存日志
         */
        public void save() {
            service.log(log);
        }
    }
}
