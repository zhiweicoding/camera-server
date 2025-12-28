package com.pura365.camera.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.cloud.*;
import com.pura365.camera.repository.CloudPlanRepository;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.CloudVideoRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.service.CloudStorageService;
import com.pura365.camera.service.CloudVideoCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 云存储相关接口
 *
 * - GET  /cloud/plans             获取云存储套餐列表
 * - POST /cloud/subscribe         创建云存储订阅支付订单
 * - GET  /cloud/videos            查询云存储视频列表
 * - GET  /cloud/subscription/{deviceId} 查询设备当前云存储订阅状态
 */
@Tag(name = "云服务接口", description = "云服务相关内部接口")
@RestController
@RequestMapping("/api/internal/cloud")
public class CloudController {

    private static final Logger log = LoggerFactory.getLogger(CloudController.class);

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private CloudVideoRepository cloudVideoRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;
    
    @Autowired
    private com.pura365.camera.repository.DeviceRepository deviceRepository;
    
    @Autowired
    private CloudStorageService cloudStorageService;

    @Autowired
    private CloudVideoCleanupService cloudVideoCleanupService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取云存储套餐列表 - GET /cloud/plans
     */
    @Operation(summary = "获取云存储套餐列表", description = "列出所有可用的云存储套餐，按类型分组返回")
    @GetMapping("/plans")
    public ApiResponse<CloudPlansResponse> getCloudPlans(@RequestAttribute("currentUserId") Long currentUserId) {
        log.info("获取云存储套餐列表 - userId={}", currentUserId);
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.orderByAsc("type", "sort_order");
        List<CloudPlan> plans = cloudPlanRepository.selectList(qw);
        
        // 按类型分组
        Map<String, List<CloudPlanVO>> groupedPlans = new LinkedHashMap<>();
        groupedPlans.put("motion", new ArrayList<>());    // 动态录像
        groupedPlans.put("fulltime", new ArrayList<>());  // 全天录像
        groupedPlans.put("traffic", new ArrayList<>());   // 4G流量
        
        if (plans != null) {
            for (CloudPlan plan : plans) {
                CloudPlanVO item = new CloudPlanVO();
                item.setId(plan.getPlanId() != null ? plan.getPlanId() : String.valueOf(plan.getId()));
                item.setName(plan.getName());
                item.setDescription(plan.getDescription());
                item.setStorageDays(plan.getStorageDays());
                item.setPrice(plan.getPrice());
                item.setOriginalPrice(plan.getOriginalPrice());
                item.setPeriod(plan.getPeriod() != null ? plan.getPeriod().getCode() : null);
                item.setFeatures(parseFeatures(plan.getFeatures()));
                item.setType(plan.getType() != null ? plan.getType().getCode() : null);
                item.setSortOrder(plan.getSortOrder());
                
                String type = plan.getType() != null ? plan.getType().getCode() : "motion";
                if (groupedPlans.containsKey(type)) {
                    groupedPlans.get(type).add(item);
                } else {
                    groupedPlans.get("motion").add(item);
                }
            }
        }
        
        CloudPlansResponse response = new CloudPlansResponse();
        response.setMotion(groupedPlans.get("motion"));
        response.setFulltime(groupedPlans.get("fulltime"));
        response.setTraffic(groupedPlans.get("traffic"));
        
        return ApiResponse.success(response);
    }

    /**
     * 订阅云存储 - POST /cloud/subscribe
     * 这里只创建支付订单，不直接写入 CloudSubscription（支付成功后再写入）。
     */
    @Operation(summary = "订阅云存储", description = "创建云存储套餐的支付订单，返回支付参数")
    @PostMapping("/subscribe")
    public ApiResponse<CloudSubscribeResponse> subscribe(@RequestAttribute("currentUserId") Long currentUserId,
                                                         @RequestBody CloudSubscribeRequest request) {
        log.info("订阅云存储 - userId={}, request={}", currentUserId, request);
        String deviceId = request.getDeviceId();
        String planId = request.getPlanId();
        String paymentMethod = request.getPaymentMethod();
        if (deviceId == null || deviceId.isEmpty() || planId == null || planId.isEmpty()) {
            log.warn("订阅云存储失败 - 参数为空, userId={}", currentUserId);
            return ApiResponse.error(400, "device_id 和 plan_id 不能为空");
        }
        if (paymentMethod == null || paymentMethod.isEmpty()) {
            paymentMethod = "wechat"; // 默认微信
        }
        // 校验设备归属
        if (!hasUserDevice(currentUserId, deviceId)) {
            return ApiResponse.error(403, "无权操作该设备");
        }
        // 查套餐
        CloudPlan plan = findPlanByPlanId(planId);
        if (plan == null) {
            return ApiResponse.error(404, "云存储套餐不存在");
        }

        BigDecimal amount = plan.getPrice();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        // 这里原项目中是通过 PaymentOrder 表统一处理支付，这里我们只返回一个模拟的订单信息
        String orderId = "order_" + System.currentTimeMillis();
        
        CloudSubscribeResponse response = new CloudSubscribeResponse();
        response.setOrderId(orderId);
        response.setAmount(amount);
        response.setCurrency("CNY");
        response.setPaymentMethod(paymentMethod);

        if ("wechat".equalsIgnoreCase(paymentMethod)) {
            response.setPrepayId("mock_prepay_" + orderId);
            response.setSign("mock_sign_" + orderId);
        }

        return ApiResponse.success(response);
    }

    /**
     * 云存储视频列表 - GET /cloud/videos
     * 直接从S3云存储查询视频文件列表
     */
    @Operation(summary = "云存储视频列表", description = "分页查询某设备的云存储视频")
    @GetMapping("/videos")
    public ApiResponse<CloudVideoListResponse> listCloudVideos(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID", required = true, example = "DEVICE123456")
            @RequestParam("device_id") String deviceId,
            @Parameter(description = "查询日期（格式: yyyy-MM-dd）", example = "2023-12-13")
            @RequestParam(value = "date", required = false) String date,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {
        log.info("云存储视频列表 - userId={}, deviceId={}, date={}, page={}, pageSize={}", 
                currentUserId, deviceId, date, page, pageSize);
        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id 不能为空");
        }
        if (!hasUserDevice(currentUserId, deviceId)) {
            log.warn("云存储视频列表 - 无权查看, userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(403, "无权查看该设备");
        }
        if (page < 1) page = 1;
        if (pageSize <= 0) pageSize = 20;

        // 直接从云存储查询视频列表
        List<Map<String, Object>> allObjects = cloudStorageService.listVideosFromCloud(deviceId, date);

        // 只保留文件名中带有 "M" 的视频文件，并为其匹配对应的 jpg 缩略图
        List<Map<String, Object>> filteredVideos = filterVideosWithThumbnails(allObjects);

        // 手动分页
        int total = filteredVideos.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<CloudVideoVO> videoList = new ArrayList<>();
        if (fromIndex < total) {
            List<Map<String, Object>> subList = filteredVideos.subList(fromIndex, toIndex);
            for (Map<String, Object> video : subList) {
                CloudVideoVO vo = new CloudVideoVO();

                // video_id 兼容 old key "id"
                Object videoIdObj = video.get("video_id");
                if (videoIdObj == null) {
                    videoIdObj = video.get("id");
                }
                vo.setVideoId(videoIdObj != null ? String.valueOf(videoIdObj) : null);

                vo.setDeviceId((String) video.get("device_id"));
                vo.setType((String) video.get("type"));
                vo.setTitle((String) video.get("title"));

                Object thumbObj = video.get("thumbnail");
                if (thumbObj == null) {
                    thumbObj = video.get("thumbnail_url");
                }
                vo.setThumbnail(thumbObj != null ? String.valueOf(thumbObj) : null);

                vo.setVideoUrl((String) video.get("video_url"));

                Object durationObj = video.get("duration");
                if (durationObj instanceof Number) {
                    vo.setDuration(((Number) durationObj).intValue());
                } else {
                    vo.setDuration(null);
                }

                vo.setCreatedAt((String) video.get("created_at"));
                videoList.add(vo);
            }
        }

        CloudVideoListResponse response = new CloudVideoListResponse();
        response.setList(videoList);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        return ApiResponse.success(response);
    }

    /**
     * 获取设备云存储订阅状态 - GET /cloud/subscription/{deviceId}
     */
    @Operation(summary = "获取云存订阅状态", description = "获取某设备当前云存储订阅信息")
    @GetMapping("/subscription/{deviceId}")
    public ApiResponse<CloudSubscriptionVO> getSubscription(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID", required = true, example = "DEVICE123456")
            @PathVariable("deviceId") String deviceId) {
        log.info("获取云存订阅状态 - userId={}, deviceId={}", currentUserId, deviceId);
        if (!hasUserDevice(currentUserId, deviceId)) {
            log.warn("获取云存订阅状态 - 无权查看, userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(403, "无权查看该设备");
        }
        QueryWrapper<CloudSubscription> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudSubscription::getUserId, currentUserId)
                .eq(CloudSubscription::getDeviceId, deviceId)
                .orderByDesc(CloudSubscription::getExpireAt)
                .last("limit 1");
        CloudSubscription sub = cloudSubscriptionRepository.selectOne(qw);

        CloudSubscriptionVO response = new CloudSubscriptionVO();
        boolean isSubscribed = sub != null && (sub.getExpireAt() == null || sub.getExpireAt().after(new Date()));
        response.setIsSubscribed(isSubscribed);
        if (isSubscribed && sub != null) {
            response.setPlanId(sub.getPlanId());
            response.setPlanName(sub.getPlanName());
            response.setExpireAt(sub.getExpireAt() != null ? formatIsoTime(sub.getExpireAt()) : null);
            response.setAutoRenew(sub.getAutoRenew() != null && sub.getAutoRenew() == 1);
        } else {
            response.setPlanId(null);
            response.setPlanName(null);
            response.setExpireAt(null);
            response.setAutoRenew(false);
        }
        return ApiResponse.success(response);
    }
    
    /**
     * 手动上传视频文件到云存储（用于测试）
     * - 用 S3 兼容方式直接 PUT 到七牛云 / Vultr
     * - 使用的 endpoint/region/AK/SK 与下发给摄像头的配置保持一致（不在响应里返回密钥）
     */
    @Operation(summary = "手动上传视频文件到云存储", description = "用于联调：通过 S3 兼容接口把文件上传到云存储。bucket/key 可选，不传则使用默认 bucket 并自动生成 key。")
    @PostMapping(value = "/upload-test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManualUploadResponse> uploadTest(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestParam("device_id") String deviceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "bucket", required = false) String bucket,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "content_type", required = false) String contentType
    ) {
        log.info("上传测试文件 - userId={}, deviceId={}, fileName={}", 
                currentUserId, deviceId, file != null ? file.getOriginalFilename() : null);
        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id 不能为空");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "file 不能为空");
        }
        // 校验设备归属
        if (!hasUserDevice(currentUserId, deviceId)) {
            return ApiResponse.error(403, "无权操作该设备");
        }

        String ct = (contentType != null && !contentType.trim().isEmpty()) ? contentType.trim() : file.getContentType();

        try (InputStream in = file.getInputStream()) {
            ManualUploadResponse resp = cloudStorageService.uploadObjectForTest(
                deviceId,
                bucket,
                key,
                file.getOriginalFilename(),
                ct,
                file.getSize(),
                in
            );

            if (resp == null) {
                return ApiResponse.error(404, "设备不存在");
            }
            if (resp.getUploaded() != null && resp.getUploaded()) {
                return ApiResponse.success(resp);
            }
            return ApiResponse.error(500, "上传失败");
        } catch (Exception e) {
            return ApiResponse.error(500, "上传异常: " + e.getMessage());
        }
    }

    /**
     * 领取免费7天云存储 - POST /cloud/claim-free
     */
    @Operation(summary = "领取免费7天云存储", description = "用户首次领取7天免费云存储，每台设备只能领取一次")
    @PostMapping("/claim-free")
    public ApiResponse<ClaimFreeCloudResponse> claimFreeTrial(@RequestAttribute("currentUserId") Long currentUserId,
                                                              @RequestBody ClaimFreeCloudRequest request) {
        log.info("领取免费云存储 - userId={}, deviceId={}", currentUserId, request.getDeviceId());
        String deviceId = request.getDeviceId();
        
        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id不能为空");
        }
        
        // 验证设备归属
        if (!hasUserDevice(currentUserId, deviceId)) {
            return ApiResponse.error(403, "无权操作该设备");
        }
        
        // 检查设备是否已领取
        com.pura365.camera.domain.Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }
        
        if (device.getFreeCloudClaimed() != null && device.getFreeCloudClaimed() == 1) {
            return ApiResponse.error(400, "该设备已领取过免费云存储");
        }
        
        // 创建7天免费订阅
        CloudSubscription subscription = new CloudSubscription();
        subscription.setUserId(currentUserId);
        subscription.setDeviceId(deviceId);
        subscription.setPlanId("free-trial-7d");
        subscription.setPlanName("7天免费试用");
        
        // 设置7天后过期
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        subscription.setExpireAt(calendar.getTime());
        subscription.setAutoRenew(0);
        subscription.setCreatedAt(new Date());
        subscription.setUpdatedAt(new Date());
        
        cloudSubscriptionRepository.insert(subscription);
        
        // 标记设备已领取
        device.setFreeCloudClaimed(1);
        deviceRepository.updateById(device);
        
        ClaimFreeCloudResponse response = new ClaimFreeCloudResponse();
        response.setClaimed(true);
        response.setExpireAt(formatIsoTime(subscription.getExpireAt()));
        
        return ApiResponse.success(response);
    }

    // ===== 云存储清理测试接口 =====

    /**
     * 测试清理单个设备的过期云存储文件
     * POST /api/internal/cloud/cleanup/device/{deviceId}
     */
    @Operation(summary = "清理设备过期云存储", description = "根据套餐循环天数清理指定设备的过期云存储文件")
    @PostMapping("/cleanup/device/{deviceId}")
    public ApiResponse<Map<String, Object>> cleanupDeviceVideos(
            @Parameter(description = "设备ID", required = true) @PathVariable String deviceId) {
        log.info("测试清理设备云存储 - deviceId={}", deviceId);
        
        try {
            // 获取设备的存储天数配置
            int storageDays = cloudStorageService.getStorageDaysForDevice(deviceId);
            
            // 执行清理
            int deletedCount = cloudVideoCleanupService.cleanupDevice(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("device_id", deviceId);
            result.put("storage_days", storageDays);
            result.put("deleted_count", deletedCount);
            result.put("message", deletedCount > 0 
                ? "清理完成，共删除 " + deletedCount + " 个过期文件" 
                : "没有需要清理的过期文件");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("清理设备云存储失败 - deviceId={}", deviceId, e);
            return ApiResponse.error(500, "清理失败: " + e.getMessage());
        }
    }

    /**
     * 测试清理所有设备的过期云存储文件
     * POST /api/internal/cloud/cleanup/all
     */
    @Operation(summary = "清理所有设备过期云存储", description = "清理所有有订阅的设备的过期云存储文件")
    @PostMapping("/cleanup/all")
    public ApiResponse<Map<String, Object>> cleanupAllDevicesVideos() {
        log.info("测试清理所有设备云存储");
        
        try {
            int totalDeleted = cloudVideoCleanupService.cleanupAllDevices();
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_deleted", totalDeleted);
            result.put("message", "清理完成，共删除 " + totalDeleted + " 个过期文件");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("清理所有设备云存储失败", e);
            return ApiResponse.error(500, "清理失败: " + e.getMessage());
        }
    }

    /**
     * 查询设备的云存储循环天数配置
     * GET /api/internal/cloud/storage-days/{deviceId}
     */
    @Operation(summary = "查询设备存储天数", description = "获取设备当前套餐的云存储循环天数")
    @GetMapping("/storage-days/{deviceId}")
    public ApiResponse<Map<String, Object>> getDeviceStorageDays(
            @Parameter(description = "设备ID", required = true) @PathVariable String deviceId) {
        log.info("查询设备存储天数 - deviceId={}", deviceId);
        
        try {
            int storageDays = cloudStorageService.getStorageDaysForDevice(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("device_id", deviceId);
            result.put("storage_days", storageDays);
            result.put("has_subscription", storageDays > 0);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询设备存储天数失败 - deviceId={}", deviceId, e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 异步清理单个设备的过期云存储文件（立即返回，后台执行）
     * POST /api/internal/cloud/cleanup/device/{deviceId}/async
     */
    @Operation(summary = "异步清理设备过期云存储", description = "触发后立即返回，清理在后台异步执行")
    @PostMapping("/cleanup/device/{deviceId}/async")
    public ApiResponse<Map<String, Object>> cleanupDeviceVideosAsync(
            @Parameter(description = "设备ID", required = true) @PathVariable String deviceId) {
        log.info("异步清理设备云存储 - deviceId={}", deviceId);
        
        try {
            int storageDays = cloudStorageService.getStorageDaysForDevice(deviceId);
            
            // 异步执行，不等待结果
            cloudVideoCleanupService.cleanupDeviceAsync(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("device_id", deviceId);
            result.put("storage_days", storageDays);
            result.put("status", "submitted");
            result.put("message", "清理任务已提交，正在后台执行");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("提交异步清理任务失败 - deviceId={}", deviceId, e);
            return ApiResponse.error(500, "提交失败: " + e.getMessage());
        }
    }

    /**
     * 异步清理所有设备的过期云存储文件（立即返回，后台执行）
     * POST /api/internal/cloud/cleanup/all/async
     */
    @Operation(summary = "异步清理所有设备过期云存储", description = "触发后立即返回，清理在后台异步执行")
    @PostMapping("/cleanup/all/async")
    public ApiResponse<Map<String, Object>> cleanupAllDevicesVideosAsync() {
        log.info("异步清理所有设备云存储");
        
        try {
            // 异步执行，立即返回
            new Thread(() -> {
                try {
                    cloudVideoCleanupService.cleanupAllDevices();
                } catch (Exception e) {
                    log.error("异步清理所有设备失败", e);
                }
            }).start();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "submitted");
            result.put("message", "清理任务已提交，正在后台执行，请查看日志了解进度");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("提交异步清理任务失败", e);
            return ApiResponse.error(500, "提交失败: " + e.getMessage());
        }
    }

    // ===== 私有辅助方法 =====

    private CloudPlan findPlanByPlanId(String planId) {
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getPlanId, planId).last("limit 1");
        CloudPlan plan = cloudPlanRepository.selectOne(qw);
        if (plan == null) {
            try {
                Long id = Long.parseLong(planId);
                plan = cloudPlanRepository.selectById(id);
            } catch (NumberFormatException ignore) {
            }
        }
        return plan;
    }

    /**
     * 过滤出文件名带 "M" 的视频，并根据规则绑定对应的 jpg 缩略图。
     *
     * 约定：
     *   - 视频文件：095337M0021.mp4、095358M0021.mp4（格式：6位数字+M+4位数字.mp4）
     *   - 对应缩略图：095337.jpg、095358.jpg（M前面的部分）
     */
    private List<Map<String, Object>> filterVideosWithThumbnails(List<Map<String, Object>> allObjects) {
        if (allObjects == null || allObjects.isEmpty()) {
            return Collections.emptyList();
        }

        // 第一步：收集所有 jpg 缩略图 -> Map<文件名(不带扩展名), URL>
        // 例如："095337.jpg" -> jpgUrlMap.put("095337", "https://...")
        Map<String, String> jpgUrlMap = new HashMap<>();
        for (Map<String, Object> obj : allObjects) {
            Object fileNameObj = obj.get("file_name");
            if (!(fileNameObj instanceof String)) {
                continue;
            }
            String fileName = (String) fileNameObj;
            String lower = fileName.toLowerCase(Locale.ROOT);
            
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                // 095337.jpg -> 去掉 .jpg 后 -> "095337"
                int dot = fileName.lastIndexOf('.');
                String baseName = (dot > 0) ? fileName.substring(0, dot) : fileName;
                
                // 取URL，优先用 video_url（CloudStorageService 把 URL 放在这里）
                Object urlObj = obj.get("video_url");
                if (urlObj != null) {
                    jpgUrlMap.put(baseName, String.valueOf(urlObj));
                }
            }
        }

        // 第二步：过滤出文件名带M的视频，并给它匹配jpg
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> obj : allObjects) {
            Object fileNameObj = obj.get("file_name");
            if (!(fileNameObj instanceof String)) {
                continue;
            }
            String fileName = (String) fileNameObj;
            String lower = fileName.toLowerCase(Locale.ROOT);

            // 必须是视频文件
            if (!(lower.endsWith(".mp4") || lower.endsWith(".h264") || lower.endsWith(".avi")
                    || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".ts"))) {
                continue;
            }

            // 必须包含M
            int mIndex = fileName.indexOf('m');
            if (mIndex <= 0) {
                continue;
            }

            // 取M之前的部分作为缩略图的基名
            // 例：095337M0021.mp4 -> "095337"
            String jpgBaseName = fileName.substring(0, mIndex);
            String thumbnailUrl = jpgUrlMap.get(jpgBaseName);

            // 拷贝原数据
            Map<String, Object> copy = new HashMap<>(obj);
            if (thumbnailUrl != null) {
                copy.put("thumbnail", thumbnailUrl);
            } else {
                copy.put("thumbnail", null);
            }

            result.add(copy);
        }

        return result;
    }

    private boolean hasUserDevice(Long userId, String deviceId) {
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        Integer count = userDeviceRepository.selectCount(qw).intValue();
        return count != null && count > 0;
    }

    private List<String> parseFeatures(String features) {
        if (features == null || features.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String f = features.trim();
        try {
            if (f.startsWith("[")) {
                return objectMapper.readValue(f, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignore) {
        }
        String[] arr = f.split(",");
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            if (s != null) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    private String formatIsoTime(Date date) {
        if (date == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }
}