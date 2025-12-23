package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.AppMessage;
import com.pura365.camera.domain.Device;
import com.pura365.camera.model.MessageListResponse;
import com.pura365.camera.model.MessageVO;
import com.pura365.camera.repository.AppMessageRepository;
import com.pura365.camera.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息服务
 */
@Service
public class MessageService {

    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final AppMessageRepository appMessageRepository;
    private final DeviceRepository deviceRepository;
    private final JPushService jPushService;

    public MessageService(AppMessageRepository appMessageRepository,
                          DeviceRepository deviceRepository,
                          JPushService jPushService) {
        this.appMessageRepository = appMessageRepository;
        this.deviceRepository = deviceRepository;
        this.jPushService = jPushService;
    }

    /**
     * 分页查询消息列表
     */
    public MessageListResponse listMessages(Long userId, String deviceId, String date, String type, int page, int pageSize) {
        page = Math.max(1, page);
        pageSize = pageSize <= 0 ? 20 : pageSize;
        int offset = (page - 1) * pageSize;

        QueryWrapper<AppMessage> queryWrapper = buildQueryWrapper(userId, deviceId, date, type);

        int total = appMessageRepository.selectCount(queryWrapper).intValue();
        List<AppMessage> messages = appMessageRepository.selectList(
                queryWrapper.last("LIMIT " + offset + "," + pageSize));

        List<MessageVO> voList = convertToVOList(messages);

        return new MessageListResponse(voList, total, page, pageSize);
    }

    /**
     * 标记消息已读
     * @return true 表示成功，false 表示消息不存在或无权限
     */
    public boolean markAsRead(Long userId, Long messageId) {
        AppMessage message = getMessageByIdAndUser(messageId, userId);
        if (message == null) {
            return false;
        }
        if (message.getIsRead() == null || message.getIsRead() == 0) {
            message.setIsRead(1);
            appMessageRepository.updateById(message);
        }
        return true;
    }

    /**
     * 删除消息
     * @return true 表示成功，false 表示消息不存在或无权限
     */
    public boolean deleteMessage(Long userId, Long messageId) {
        AppMessage message = getMessageByIdAndUser(messageId, userId);
        if (message == null) {
            return false;
        }
        appMessageRepository.deleteById(messageId);
        return true;
    }

    /**
     * 获取未读消息数量
     */
    public int getUnreadCount(Long userId) {
        LambdaQueryWrapper<AppMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppMessage::getUserId, userId)
               .eq(AppMessage::getIsRead, 0);
        return appMessageRepository.selectCount(wrapper).intValue();
    }

    // ============== 私有方法 ==============

    private QueryWrapper<AppMessage> buildQueryWrapper(Long userId, String deviceId, String date, String type) {
        QueryWrapper<AppMessage> qw = new QueryWrapper<>();
        LambdaQueryWrapper<AppMessage> lambda = qw.lambda();

        lambda.eq(AppMessage::getUserId, userId);

        if (StringUtils.hasText(deviceId)) {
            lambda.eq(AppMessage::getDeviceId, deviceId);
        }
        if (StringUtils.hasText(type)) {
            lambda.eq(AppMessage::getType, type);
        }
        if (StringUtils.hasText(date)) {
            qw.apply("DATE(created_at) = {0}", date);
        }
        qw.orderByDesc("created_at");

        return qw;
    }

    private AppMessage getMessageByIdAndUser(Long messageId, Long userId) {
        AppMessage message = appMessageRepository.selectById(messageId);
        if (message == null || message.getUserId() == null || !message.getUserId().equals(userId)) {
            return null;
        }
        return message;
    }

    private List<MessageVO> convertToVOList(List<AppMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取设备名称，避免 N+1 查询
        Map<String, String> deviceNameMap = loadDeviceNames(messages);

        return messages.stream()
                .map(msg -> convertToVO(msg, deviceNameMap))
                .collect(Collectors.toList());
    }

    private Map<String, String> loadDeviceNames(List<AppMessage> messages) {
        Set<String> deviceIds = messages.stream()
                .map(AppMessage::getDeviceId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Device::getId, deviceIds);
        List<Device> devices = deviceRepository.selectList(wrapper);

        return devices.stream()
                .collect(Collectors.toMap(Device::getId, Device::getName, (a, b) -> a));
    }

    private MessageVO convertToVO(AppMessage message, Map<String, String> deviceNameMap) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setType(message.getType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setDeviceId(message.getDeviceId());
        vo.setDeviceName(deviceNameMap.get(message.getDeviceId()));
        vo.setThumbnailUrl(message.getThumbnailUrl());
        vo.setVideoUrl(message.getVideoUrl());
        vo.setIsRead(message.getIsRead() != null && message.getIsRead() == 1);

        if (message.getCreatedAt() != null) {
            vo.setCreatedAt(ISO_FORMATTER.format(message.getCreatedAt().toInstant()));
        }
        return vo;
    }

    /**
     * 创建消息并推送给用户
     * 
     * @param userId       用户ID
     * @param deviceId     设备ID
     * @param type         消息类型
     * @param title        消息标题
     * @param content      消息内容
     * @param thumbnailUrl 缩略图URL
     * @param videoUrl     视频URL
     * @return 创建的消息ID
     */
    public Long createMessageAndPush(Long userId, String deviceId, String type, 
                                      String title, String content, 
                                      String thumbnailUrl, String videoUrl) {
        // 创建消息记录
        AppMessage message = new AppMessage();
        message.setUserId(userId);
        message.setDeviceId(deviceId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setThumbnailUrl(thumbnailUrl);
        message.setVideoUrl(videoUrl);
        message.setIsRead(0);
        message.setCreatedAt(new Date());
        
        appMessageRepository.insert(message);
        
        // 触发极光推送
        pushMessageToUser(userId, deviceId, title, content, thumbnailUrl, videoUrl);
        
        return message.getId();
    }

    /**
     * 推送消息给用户
     */
    private void pushMessageToUser(Long userId, String deviceId, String title, 
                                    String content, String thumbnailUrl, String videoUrl) {
        try {
            Map<String, String> extras = new java.util.HashMap<>();
            if (deviceId != null) {
                extras.put("device_id", deviceId);
            }
            if (thumbnailUrl != null) {
                extras.put("thumbnail_url", thumbnailUrl);
            }
            if (videoUrl != null) {
                extras.put("video_url", videoUrl);
            }
            
            boolean success = jPushService.pushToUser(userId, title, content, extras);
            if (success) {
                org.slf4j.LoggerFactory.getLogger(MessageService.class)
                    .info("已向用户 {} 推送消息: {}", userId, title);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MessageService.class)
                .error("推送消息给用户 {} 失败", userId, e);
        }
    }
}
