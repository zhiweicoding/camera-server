package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.AppLog;
import com.pura365.camera.domain.AppMessage;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.DeviceBinding;
import com.pura365.camera.domain.DeviceShare;
import com.pura365.camera.domain.Feedback;
import com.pura365.camera.domain.LiveStream;
import com.pura365.camera.domain.User;
import com.pura365.camera.domain.UserAuth;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.domain.UserOperationLog;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.domain.UserToken;
import com.pura365.camera.domain.WifiHistory;
import com.pura365.camera.enums.UserDeviceRole;
import com.pura365.camera.repository.AppLogRepository;
import com.pura365.camera.repository.AppMessageRepository;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.DeviceBindingRepository;
import com.pura365.camera.repository.DeviceShareRepository;
import com.pura365.camera.repository.FeedbackRepository;
import com.pura365.camera.repository.LiveStreamRepository;
import com.pura365.camera.repository.UserAuthRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.repository.UserOperationLogRepository;
import com.pura365.camera.repository.UserPushTokenRepository;
import com.pura365.camera.repository.UserRepository;
import com.pura365.camera.repository.UserTokenRepository;
import com.pura365.camera.repository.WifiHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 账号注销服务
 *
 * 删除当前用户账号以及大部分与账号直接关联的数据。
 * 支付订单等财务记录不在此处物理删除，用于后续对账与合规留存。
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private UserPushTokenRepository userPushTokenRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private DeviceBindingRepository deviceBindingRepository;

    @Autowired
    private DeviceShareRepository deviceShareRepository;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private AppMessageRepository appMessageRepository;

    @Autowired
    private WifiHistoryRepository wifiHistoryRepository;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private UserOperationLogRepository userOperationLogRepository;

    @Autowired
    private AppLogRepository appLogRepository;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> deleteCurrentAccount(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getRole() != null && user.getRole() == 3) {
            throw new RuntimeException("管理员账号不支持在 App 内注销");
        }

        Map<String, Integer> deletedCounts = new LinkedHashMap<>();

        Set<String> ownedDeviceIds = listOwnedDeviceIds(userId);
        if (!ownedDeviceIds.isEmpty()) {
            deletedCounts.put("owned_device_shares", deleteByIn(deviceShareRepository, "device_id", ownedDeviceIds));
            deletedCounts.put("owned_device_access", deleteByIn(userDeviceRepository, "device_id", ownedDeviceIds));
        }

        deletedCounts.put("device_bindings", deleteByEq(deviceBindingRepository, "user_id", userId));
        deletedCounts.put("live_streams", deleteByEq(liveStreamRepository, "user_id", userId));
        deletedCounts.put("cloud_subscriptions", deleteByEq(cloudSubscriptionRepository, "user_id", userId));
        deletedCounts.put("push_tokens", deleteByEq(userPushTokenRepository, "user_id", userId));
        deletedCounts.put("access_tokens", deleteByEq(userTokenRepository, "user_id", userId));
        deletedCounts.put("oauth_bindings", deleteByEq(userAuthRepository, "user_id", userId));
        deletedCounts.put("wifi_history", deleteByEq(wifiHistoryRepository, "user_id", userId));
        deletedCounts.put("feedback", deleteByEq(feedbackRepository, "user_id", userId));
        deletedCounts.put("messages", deleteByEq(appMessageRepository, "user_id", userId));
        deletedCounts.put("operation_logs", deleteByEq(userOperationLogRepository, "user_id", userId));
        deletedCounts.put("app_logs", deleteByEq(appLogRepository, "user_id", userId));
        deletedCounts.put("shared_device_links", deleteSharesByUser(userId));
        deletedCounts.put("user_device_links", deleteByEq(userDeviceRepository, "user_id", userId));

        int deletedUsers = userRepository.deleteById(userId);
        if (deletedUsers <= 0) {
            throw new RuntimeException("账号注销失败，请稍后重试");
        }
        deletedCounts.put("users", deletedUsers);

        log.info("账号注销完成 - userId={}, uid={}, cleanup={}", userId, user.getUid(), deletedCounts);
        return deletedCounts;
    }

    private Set<String> listOwnedDeviceIds(Long userId) {
        QueryWrapper<UserDevice> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getRole, UserDeviceRole.OWNER);

        List<UserDevice> devices = userDeviceRepository.selectList(wrapper);
        return devices.stream()
                .map(UserDevice::getDeviceId)
                .filter(deviceId -> deviceId != null && !deviceId.trim().isEmpty())
                .collect(Collectors.toSet());
    }

    private int deleteSharesByUser(Long userId) {
        QueryWrapper<DeviceShare> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(DeviceShare::getOwnerUserId, userId)
                .or()
                .eq(DeviceShare::getSharedUserId, userId);
        return deviceShareRepository.delete(wrapper);
    }

    private <T> int deleteByEq(BaseMapper<T> repository, String column, Object value) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq(column, value);
        return repository.delete(wrapper);
    }

    private <T> int deleteByIn(BaseMapper<T> repository, String column, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.in(column, values);
        return repository.delete(wrapper);
    }
}
