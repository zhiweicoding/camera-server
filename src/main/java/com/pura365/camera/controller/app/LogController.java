package com.pura365.camera.controller.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.domain.AppLog;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.repository.AppLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * App日志上报接口
 * 用于收集客户端日志，方便排查问题
 */
@Tag(name = "日志上报", description = "App日志收集接口")
@RestController
@RequestMapping("/api/app/log")
public class LogController {

    private static final Logger logger = LoggerFactory.getLogger(LogController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AppLogRepository appLogRepository;

    /**
     * 批量上传日志
     * POST /api/app/log/upload
     * Body: {
     *   "device_id": "手机设备标识",
     *   "user_id": "用户ID(可选)",
     *   "app_version": "1.0.0",
     *   "device_model": "Android/iOS",
     *   "os_version": "系统版本",
     *   "logs": [
     *     {
     *       "level": "info",
     *       "tag": "配网",
     *       "message": "操作描述",
     *       "timestamp": "2025-12-23T10:00:00.000Z",
     *       "extra": {"key": "value"}
     *     }
     *   ]
     * }
     */
    @Operation(summary = "上传日志", description = "批量上传App日志")
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadLogs(@RequestBody Map<String, Object> body) {
        try {
            String deviceId = (String) body.get("device_id");
            Object userIdObj = body.get("user_id");
            Long userId = null;
            if (userIdObj != null) {
                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else if (userIdObj instanceof String && !((String) userIdObj).isEmpty()) {
                    userId = Long.parseLong((String) userIdObj);
                }
            }
            String appVersion = (String) body.get("app_version");
            String deviceModel = (String) body.get("device_model");
            String osVersion = (String) body.get("os_version");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) body.get("logs");
            
            if (logs == null || logs.isEmpty()) {
                return ApiResponse.error(400, "logs 不能为空");
            }

            Date now = new Date();
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            
            int savedCount = 0;
            for (Map<String, Object> logEntry : logs) {
                try {
                    AppLog appLog = new AppLog();
                    appLog.setUserId(userId);
                    appLog.setDeviceId(deviceId);
                    appLog.setAppVersion(appVersion);
                    appLog.setDeviceModel(deviceModel);
                    appLog.setOsVersion(osVersion);
                    appLog.setLevel((String) logEntry.get("level"));
                    appLog.setTag((String) logEntry.get("tag"));
                    appLog.setMessage((String) logEntry.get("message"));
                    
                    // 解析客户端时间戳
                    String timestamp = (String) logEntry.get("timestamp");
                    if (timestamp != null) {
                        try {
                            appLog.setClientTime(isoFormat.parse(timestamp.replace("Z", "")));
                        } catch (Exception e) {
                            appLog.setClientTime(now);
                        }
                    } else {
                        appLog.setClientTime(now);
                    }
                    
                    // 转换 extra 为 JSON 字符串
                    Object extra = logEntry.get("extra");
                    if (extra != null) {
                        appLog.setExtra(objectMapper.writeValueAsString(extra));
                    }
                    
                    appLog.setCreatedAt(now);
                    
                    appLogRepository.insert(appLog);
                    savedCount++;
                    
                    // 同时输出到服务器日志便于实时查看
                    logger.info("[APP_LOG] [{}/{}] [{}] {} - {} | extra: {}", 
                        deviceModel, appVersion, 
                        appLog.getLevel(), 
                        appLog.getTag(), 
                        appLog.getMessage(),
                        appLog.getExtra());
                    
                } catch (Exception e) {
                    logger.error("保存日志条目失败: {}", e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("saved_count", savedCount);
            result.put("total_count", logs.size());
            result.put("received_at", isoFormat.format(now) + "Z");
            
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            logger.error("日志上传失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "日志上传失败: " + e.getMessage());
        }
    }

    /**
     * 查询日志（管理后台使用）
     * GET /api/app/log/list?user_id=xxx&tag=配网&level=error&page=1&size=20
     */
    @Operation(summary = "查询日志", description = "根据条件查询App日志")
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> listLogs(
            @RequestParam(required = false) Long user_id,
            @RequestParam(required = false) String device_id,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AppLog> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            
            if (user_id != null) {
                wrapper.eq("user_id", user_id);
            }
            if (device_id != null && !device_id.isEmpty()) {
                wrapper.eq("device_id", device_id);
            }
            if (tag != null && !tag.isEmpty()) {
                wrapper.eq("tag", tag);
            }
            if (level != null && !level.isEmpty()) {
                wrapper.eq("level", level);
            }
            
            wrapper.orderByDesc("created_at");
            
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<AppLog> pageParam = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<AppLog> result = 
                appLogRepository.selectPage(pageParam, wrapper);
            
            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getRecords());
            data.put("total", result.getTotal());
            data.put("page", page);
            data.put("size", size);
            
            return ApiResponse.success(data);
            
        } catch (Exception e) {
            logger.error("查询日志失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "查询日志失败: " + e.getMessage());
        }
    }
}
