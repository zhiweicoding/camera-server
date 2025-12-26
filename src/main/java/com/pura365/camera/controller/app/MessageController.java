package com.pura365.camera.controller.app;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.MessageListResponse;
import com.pura365.camera.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * 消息中心接口
 */
@Tag(name = "消息管理", description = "用户消息查询、标记、删除等接口")
@RestController
@RequestMapping("/api/app/messages")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageService messageService;

    @Operation(summary = "获取消息列表", description = "分页查询当前用户的消息列表，支持按设备、日期、类型筛选")
    @GetMapping
    public ApiResponse<MessageListResponse> listMessages(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @RequestParam(value = "device_id", required = false) String deviceId,
            @Parameter(description = "日期，格式 yyyy-MM-dd") @RequestParam(value = "date", required = false) String date,
            @Parameter(description = "消息类型") @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "页码，从1开始") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        log.info("获取消息列表 - userId={}, deviceId={}, date={}, type={}, page={}, pageSize={}", 
                currentUserId, deviceId, date, type, page, pageSize);
        MessageListResponse response = messageService.listMessages(currentUserId, deviceId, date, type, page, pageSize);
        return ApiResponse.success(response);
    }

    @Operation(summary = "标记消息已读")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markMessageRead(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "消息ID") @PathVariable("id") Long id) {
        log.info("标记消息已读 - userId={}, messageId={}", currentUserId, id);
        boolean success = messageService.markAsRead(currentUserId, id);
        if (!success) {
            log.warn("标记消息已读失败 - 消息不存在, userId={}, messageId={}", currentUserId, id);
            return ApiResponse.error(404, "消息不存在");
        }
        return ApiResponse.success("标记成功", null);
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMessage(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "消息ID") @PathVariable("id") Long id) {
        log.info("删除消息 - userId={}, messageId={}", currentUserId, id);
        boolean success = messageService.deleteMessage(currentUserId, id);
        if (!success) {
            log.warn("删除消息失败 - 消息不存在, userId={}, messageId={}", currentUserId, id);
            return ApiResponse.error(404, "消息不存在");
        }
        return ApiResponse.success("删除成功", null);
    }

    @Operation(summary = "获取未读消息数量")
    @GetMapping("/unread/count")
    public ApiResponse<Map<String, Integer>> getUnreadCount(
            @RequestAttribute("currentUserId") Long currentUserId) {
        log.info("获取未读消息数量 - userId={}", currentUserId);
        int count = messageService.getUnreadCount(currentUserId);
        return ApiResponse.success(Collections.singletonMap("count", count));
    }
}
