package com.pura365.camera.controller.device;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 回放视频接口
 *
 * 对应 api_spec.md 中的接口:
 * - GET /videos/{id}/playback
 */
@Tag(name = "视频回放", description = "视频回放相关接口")
@RestController
@RequestMapping("/api/device/videos")
public class VideoPlaybackController {

    private static final Logger log = LoggerFactory.getLogger(VideoPlaybackController.class);

    @Autowired
    private LocalVideoRepository localVideoRepository;

    @Autowired
    private CloudVideoRepository cloudVideoRepository;

    @Autowired
    private DeviceRecordRepository deviceRecordRepository;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    /**
     * 获取回放视频流信息 - GET /videos/{id}/playback
     */
    @Operation(summary = "获取回放视频", description = "根据视频ID获取回放视频播放地址")
    @GetMapping("/{id}/playback")
    public ApiResponse<Map<String, Object>> getPlayback(@RequestAttribute("currentUserId") Long currentUserId,
                                                        @PathVariable("id") String videoId) {
        log.info("获取回放视频 - userId={}, videoId={}", currentUserId, videoId);
        // 1. 先查本地录像
        LocalVideo local = findLocalVideo(videoId);
        if (local != null) {
            if (!hasUserDevice(currentUserId, local.getDeviceId())) {
                log.warn("获取回放视频失败 - 无权限, userId={}, videoId={}", currentUserId, videoId);
                return ApiResponse.error(403, "无权访问该视频");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("video_url", local.getVideoUrl());
            data.put("hls_url", null);
            data.put("duration", local.getDuration());
            data.put("expires_at", null);
            return ApiResponse.success(data);
        }

        // 2. 再查云存录像
        CloudVideo cloud = findCloudVideo(videoId);
        if (cloud != null) {
            if (!hasUserDevice(currentUserId, cloud.getDeviceId())) {
                return ApiResponse.error(403, "无权访问该视频");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("video_url", cloud.getVideoUrl());
            data.put("hls_url", null);
            data.put("duration", cloud.getDuration());

            // 结合云存订阅信息给出过期时间（如果有的话）
            Date expireAt = findCloudExpireAt(currentUserId, cloud.getDeviceId());
            data.put("expires_at", expireAt != null ? formatIsoTime(expireAt) : null);
            return ApiResponse.success(data);
        }

        // 3. 最后查设备录制会话表（手动录制）
        DeviceRecord record = findDeviceRecord(videoId);
        if (record != null) {
            if (!hasUserDevice(currentUserId, record.getDeviceId())) {
                return ApiResponse.error(403, "无权访问该视频");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("video_url", record.getVideoUrl());
            data.put("hls_url", null);
            Integer duration = record.getDuration();
            if (duration == null && record.getStartedAt() != null && record.getEndedAt() != null) {
                long seconds = (record.getEndedAt().getTime() - record.getStartedAt().getTime()) / 1000;
                duration = (int) Math.max(seconds, 0);
            }
            data.put("duration", duration);
            data.put("expires_at", null);
            return ApiResponse.success(data);
        }

        log.warn("获取回放视频失败 - 视频不存在, userId={}, videoId={}", currentUserId, videoId);
        return ApiResponse.error(404, "视频不存在");
    }

    // ===== 私有帮助方法 =====

    private LocalVideo findLocalVideo(String videoId) {
        QueryWrapper<LocalVideo> qw = new QueryWrapper<>();
        qw.lambda().eq(LocalVideo::getVideoId, videoId).last("limit 1");
        return localVideoRepository.selectOne(qw);
    }

    private CloudVideo findCloudVideo(String videoId) {
        QueryWrapper<CloudVideo> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudVideo::getVideoId, videoId).last("limit 1");
        return cloudVideoRepository.selectOne(qw);
    }

    private DeviceRecord findDeviceRecord(String recordId) {
        QueryWrapper<DeviceRecord> qw = new QueryWrapper<>();
        qw.lambda().eq(DeviceRecord::getRecordId, recordId).last("limit 1");
        return deviceRecordRepository.selectOne(qw);
    }

    private Date findCloudExpireAt(Long userId, String deviceId) {
        QueryWrapper<CloudSubscription> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudSubscription::getUserId, userId)
                .eq(CloudSubscription::getDeviceId, deviceId)
                .orderByDesc(CloudSubscription::getExpireAt)
                .last("limit 1");
        CloudSubscription sub = cloudSubscriptionRepository.selectOne(qw);
        return sub != null ? sub.getExpireAt() : null;
    }

    private boolean hasUserDevice(Long userId, String deviceId) {
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        Integer count = userDeviceRepository.selectCount(qw).intValue();
        return count != null && count > 0;
    }

    private String formatIsoTime(Date date) {
        if (date == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }
}