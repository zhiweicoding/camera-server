package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.AppMessage;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.model.MessageListResponse;
import com.pura365.camera.model.MessageVO;
import com.pura365.camera.repository.AppMessageRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 消息服务
 */
@Service
public class MessageService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageService.class);
    private static final String TYPE_DEVICE_STATUS = "device_status";
    private static final String TYPE_EVENT = "event";
    private static final Set<String> STATUS_NOTIFICATION_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("设备离线通知", "设备上线通知"))
    );

    private final AppMessageRepository appMessageRepository;
    private final DeviceRepository deviceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final JPushService jPushService;
    private final StringRedisTemplate stringRedisTemplate;
    private final long motionPushCooldownSeconds;
    private final DateTimeFormatter messageTimeFormatter;

    public MessageService(AppMessageRepository appMessageRepository,
                          DeviceRepository deviceRepository,
                          UserDeviceRepository userDeviceRepository,
                          JPushService jPushService,
                          StringRedisTemplate stringRedisTemplate,
                          @Value("${push.motion.cooldown-seconds:20}") long motionPushCooldownSeconds,
                          @Value("${app.message.timezone:Asia/Shanghai}") String messageTimeZone) {
        this.appMessageRepository = appMessageRepository;
        this.deviceRepository = deviceRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.jPushService = jPushService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.motionPushCooldownSeconds = motionPushCooldownSeconds;
        this.messageTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(resolveMessageZoneId(messageTimeZone));
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
        applyAccessibleDeviceFilter(wrapper, userId, null);
        excludeStatusNotifications(wrapper);
        return appMessageRepository.selectCount(wrapper).intValue();
    }

    /**
     * 删除指定用户在某台设备下的消息，用于解绑后清理历史未读。
     */
    public int deleteMessagesByUserAndDevice(Long userId, String deviceId) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return 0;
        }

        QueryWrapper<AppMessage> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(AppMessage::getUserId, userId)
                .eq(AppMessage::getDeviceId, deviceId.trim());
        return appMessageRepository.delete(wrapper);
    }

    /**
     * 删除某台设备的全部消息，用于 resetdevice 后全量清理。
     */
    public int deleteMessagesByDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return 0;
        }

        QueryWrapper<AppMessage> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(AppMessage::getDeviceId, deviceId.trim());
        return appMessageRepository.delete(wrapper);
    }

    // ============== 私有方法 ==============

    private QueryWrapper<AppMessage> buildQueryWrapper(Long userId, String deviceId, String date, String type) {
        QueryWrapper<AppMessage> qw = new QueryWrapper<>();
        LambdaQueryWrapper<AppMessage> lambda = qw.lambda();

        lambda.eq(AppMessage::getUserId, userId);
        applyAccessibleDeviceFilter(qw, userId, deviceId);

        if (StringUtils.hasText(deviceId)) {
            lambda.eq(AppMessage::getDeviceId, deviceId);
        }
        String normalizedType = StringUtils.hasText(type) ? type.trim() : null;
        if (StringUtils.hasText(normalizedType) && !isAllMessageAlias(normalizedType)) {
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

    private void applyAccessibleDeviceFilter(QueryWrapper<AppMessage> queryWrapper, Long userId, String deviceId) {
        Set<String> boundDeviceIds = loadBoundDeviceIds(userId);

        if (StringUtils.hasText(deviceId)) {
            if (!boundDeviceIds.contains(deviceId.trim())) {
                queryWrapper.apply("1 = 0");
            }
            return;
        }

        queryWrapper.and(w -> {
            w.isNull("device_id").or().eq("device_id", "");
            if (!boundDeviceIds.isEmpty()) {
                w.or().in("device_id", boundDeviceIds);
            }
        });
    }

    private boolean isDeviceStatusType(String type) {
        return StringUtils.hasText(type) && TYPE_DEVICE_STATUS.equalsIgnoreCase(type.trim());
    }

    /**
     * APP 端历史上固定传 type=event，这里兼容为“查看全部消息”。
     */
    private boolean isAllMessageAlias(String type) {
        return StringUtils.hasText(type) && TYPE_EVENT.equalsIgnoreCase(type.trim());
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
            vo.setCreatedAt(messageTimeFormatter.format(message.getCreatedAt().toInstant()));
        }
        return vo;
    }

    private Set<String> loadBoundDeviceIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId);
        List<UserDevice> bindings = userDeviceRepository.selectList(wrapper);
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptySet();
        }

        return bindings.stream()
                .map(UserDevice::getDeviceId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    private ZoneId resolveMessageZoneId(String messageTimeZone) {
        if (StringUtils.hasText(messageTimeZone)) {
            try {
                return ZoneId.of(messageTimeZone.trim());
            } catch (Exception e) {
                log.warn("消息时间配置非法，回退 Asia/Shanghai - timezone={}", messageTimeZone, e);
            }
        }
        return ZoneId.of("Asia/Shanghai");
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
        log.info("创建消息并准备推送 - messageId={}, userId={}, deviceId={}, type={}, title={}",
                message.getId(), userId, deviceId, type, title);
        
//        boolean deviceOnline = isDeviceOnline(deviceId);
//        if (!ignoreDeviceOnlineCheck && !deviceOnline) {
//            log.info("设备 {} 不在线，跳过推送", deviceId);
//            return message.getId();
//        }
        if (ignoreDeviceOnlineCheck) {
            log.info("设备 {} 不在线，ignoreDeviceOnlineCheck=true，继续推送", deviceId);
        }

        if (shouldSkipPushByCooldown(userId, deviceId, type)) {
            log.info("命中推送冷却窗口，跳过推送 userId={}, deviceId={}, type={}, messageId={}",
                    userId, deviceId, type, message.getId());
            return message.getId();
        }

        // 触发极光推送
        pushMessageToUser(message.getId(), userId, deviceId, type, title, content, thumbnailUrl, videoUrl);
        
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
    private void pushMessageToUser(Long messageId, Long userId, String deviceId, String type, String title,
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
            extras.put("message_id", String.valueOf(messageId));
            if (StringUtils.hasText(type)) {
                extras.put("type", type);
            }

            log.info("开始执行消息推送 - messageId={}, userId={}, deviceId={}, type={}, title={}, extrasKeys={}",
                    messageId, userId, deviceId, type, title, new TreeSet<>(extras.keySet()));
            
            boolean success = jPushService.pushToUser(userId, title, content, extras);
            if (success) {
                log.info("消息推送完成 - messageId={}, userId={}, deviceId={}, type={}, title={}, success=true",
                        messageId, userId, deviceId, type, title);
            } else {
                log.warn("消息推送完成 - messageId={}, userId={}, deviceId={}, type={}, title={}, success=false",
                        messageId, userId, deviceId, type, title);
            }
        } catch (Exception e) {
            log.error("推送消息给用户失败 - messageId={}, userId={}, deviceId={}, type={}, title={}",
                    messageId, userId, deviceId, type, title, e);
        }
    }
}
