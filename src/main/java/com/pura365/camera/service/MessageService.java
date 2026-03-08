package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.AppMessage;
import com.pura365.camera.domain.Device;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.model.MessageListResponse;
import com.pura365.camera.model.MessageVO;
import com.pura365.camera.repository.AppMessageRepository;
import com.pura365.camera.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 消息服务
 */
@Service
public class MessageService {

    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final String TYPE_DEVICE_STATUS = "device_status";
    private static final Set<String> STATUS_NOTIFICATION_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("设备离线通知", "设备上线通知"))
    );

    private final AppMessageRepository appMessageRepository;
    private final DeviceRepository deviceRepository;
    private final JPushService jPushService;
    private final StringRedisTemplate stringRedisTemplate;
    private final long motionPushCooldownSeconds;

    public MessageService(AppMessageRepository appMessageRepository,
                          DeviceRepository deviceRepository,
                          JPushService jPushService,
                          StringRedisTemplate stringRedisTemplate,
                          @Value("${push.motion.cooldown-seconds:20}") long motionPushCooldownSeconds) {
        this.appMessageRepository = appMessageRepository;
        this.deviceRepository = deviceRepository;
        this.jPushService = jPushService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.motionPushCooldownSeconds = motionPushCooldownSeconds;
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
        QueryWrapper<AppMessage> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(AppMessage::getUserId, userId)
                .eq(AppMessage::getIsRead, 0);
        excludeStatusNotifications(wrapper);
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
        String normalizedType = StringUtils.hasText(type) ? type.trim() : null;
        if (StringUtils.hasText(normalizedType)) {
            lambda.eq(AppMessage::getType, normalizedType);
        }
        if (StringUtils.hasText(date)) {
            qw.apply("DATE(created_at) = {0}", date);
        }
        if (!isDeviceStatusType(normalizedType)) {
            excludeStatusNotifications(qw);
        }
        qw.orderByDesc("created_at");

        return qw;
    }

    private boolean isDeviceStatusType(String type) {
        return StringUtils.hasText(type) && TYPE_DEVICE_STATUS.equalsIgnoreCase(type.trim());
    }

    private void excludeStatusNotifications(QueryWrapper<AppMessage> queryWrapper) {
        queryWrapper.and(w -> w.isNull("type").or().ne("type", TYPE_DEVICE_STATUS));
        queryWrapper.and(w -> w.isNull("title").or().notIn("title", STATUS_NOTIFICATION_TITLES));
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
        return createMessageAndPush(userId, deviceId, type, title, content, thumbnailUrl, videoUrl, false);
    }

    /**
     * 创建消息并推送给用户
     *
     * @param ignoreDeviceOnlineCheck true=忽略设备在线状态也推送（用于离线通知等场景）
     */
    public Long createMessageAndPush(Long userId, String deviceId, String type,
                                     String title, String content,
                                     String thumbnailUrl, String videoUrl,
                                     boolean ignoreDeviceOnlineCheck) {
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
        
//        boolean deviceOnline = isDeviceOnline(deviceId);
//        if (!ignoreDeviceOnlineCheck && !deviceOnline) {
//            org.slf4j.LoggerFactory.getLogger(MessageService.class)
//                .info("设备 {} 不在线，跳过推送", deviceId);
//            return message.getId();
//        }
        if (ignoreDeviceOnlineCheck) {
            org.slf4j.LoggerFactory.getLogger(MessageService.class)
                    .info("设备 {} 不在线，ignoreDeviceOnlineCheck=true，继续推送", deviceId);
        }

        if (shouldSkipPushByCooldown(userId, deviceId, type)) {
            org.slf4j.LoggerFactory.getLogger(MessageService.class)
                    .info("命中推送冷却窗口，跳过推送 userId={}, deviceId={}, type={}", userId, deviceId, type);
            return message.getId();
        }

        // 触发极光推送
        pushMessageToUser(userId, deviceId, title, content, thumbnailUrl, videoUrl);
        
        return message.getId();
    }

    /**
     * 检查设备是否在线
     */
    private boolean isDeviceOnline(String deviceId) {
        if (deviceId == null) {
            return true; // 没有设备ID时默认允许推送
        }
        Device device = deviceRepository.selectById(deviceId);
        return device != null && device.getStatus() == DeviceOnlineStatus.ONLINE;
    }

    private boolean shouldSkipPushByCooldown(Long userId, String deviceId, String type) {
        if (!"motion".equalsIgnoreCase(type)) {
            return false;
        }
        if (motionPushCooldownSeconds <= 0) {
            return false;
        }
        String redisKey = buildMotionCooldownKey(userId, deviceId);
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", motionPushCooldownSeconds, TimeUnit.SECONDS);
        return Boolean.FALSE.equals(first);
    }

    private String buildMotionCooldownKey(Long userId, String deviceId) {
        String safeDeviceId = StringUtils.hasText(deviceId) ? deviceId.trim() : "unknown";
        return "push:cooldown:motion:" + userId + ":" + safeDeviceId;
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
            } else {
                org.slf4j.LoggerFactory.getLogger(MessageService.class)
                    .warn("向用户 {} 推送消息返回false: title={}, deviceId={}", userId, title, deviceId);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MessageService.class)
                .error("推送消息给用户 {} 失败", userId, e);
        }
    }
}
