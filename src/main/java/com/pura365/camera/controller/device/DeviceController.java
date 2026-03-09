package com.pura365.camera.controller.device;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.device.*;
import com.pura365.camera.domain.Device;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 设备管理控制器
 * <p>
 * 提供App端设备相关的HTTP接口，包括：
 * <ul>
 *     <li>设备列表查询</li>
 *     <li>设备详情查询</li>
 *     <li>添加/绑定设备</li>
 *     <li>删除/解绑设备</li>
 *     <li>更新设备信息</li>
 *     <li>设备设置管理</li>
 *     <li>本地录像列表</li>
 *     <li>云台PTZ控制</li>
 * </ul>
 *
 * @author camera-server
 */
@Tag(name = "设备管理", description = "设备增删改查、设置、录像、云台控制等管理接口")
@RestController
@RequestMapping("/api/device/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    /**
     * HTTP状态码：请求参数错误
     */
    private static final int HTTP_BAD_REQUEST = 400;

    /**
     * HTTP状态码：无权限
     */
    private static final int HTTP_FORBIDDEN = 403;

    /**
     * HTTP状态码：资源不存在
     */
    private static final int HTTP_NOT_FOUND = 404;

    /**
     * HTTP状态码：服务器内部错误
     */
    private static final int HTTP_INTERNAL_ERROR = 500;

    /**
     * 错误消息：设备不存在
     */
    private static final String MSG_DEVICE_NOT_FOUND = "设备不存在";

    /**
     * 错误消息：无权操作该设备
     */
    private static final String MSG_NO_PERMISSION = "无权操作该设备";

    /**
     * 错误消息：设备ID不能为空
     */
    private static final String MSG_DEVICE_ID_REQUIRED = "设备ID不能为空";

    /**
     * 错误消息：方向参数不能为空
     */
    private static final String MSG_DIRECTION_REQUIRED = "方向参数不能为空";

    /**
     * 错误消息：未提供可更新设置项
     */
    private static final String MSG_NO_SETTINGS_PROVIDED = "未提供可记录的设备设置项";

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private com.pura365.camera.service.CloudStorageService cloudStorageService;

    /**
     * 获取当前用户的设备列表
     *
     * @param currentUserId 当前登录用户ID（由拦截器注入）
     * @return 设备列表
     */
    @Operation(summary = "获取设备列表", description = "获取当前用户绑定的所有设备")
    @GetMapping
    public ApiResponse<List<DeviceListItemVO>> listDevices(
            @RequestAttribute("currentUserId") Long currentUserId) {
        log.info("获取设备列表 - userId={}", currentUserId);
        List<DeviceListItemVO> devices = deviceService.listDevices(currentUserId);
        log.info("获取设备列表成功 - userId={}, count={}", currentUserId, devices.size());
        return ApiResponse.success(devices);
    }

    /**
     * 获取设备详情
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @return 设备详细信息
     */
    @Operation(summary = "获取设备详情", description = "获取指定设备的详细信息，包括设置、云存储状态等")
    @GetMapping("/{id}/info")
    public ApiResponse<DeviceDetailVO> getDeviceInfo(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId) {
        log.info("获取设备详情 - userId={}, deviceId={}", currentUserId, deviceId);
        DeviceDetailVO detail = deviceService.getDeviceDetail(currentUserId, deviceId);
        if (detail == null) {
            log.warn("获取设备详情失败，设备不存在 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_NOT_FOUND, MSG_DEVICE_NOT_FOUND);
        }
        return ApiResponse.success(detail);
    }

    /**
     * 添加/绑定设备
     *
     * @param currentUserId 当前登录用户ID
     * @param request       添加设备请求参数
     * @return 添加后的设备基础信息
     */
    @Operation(summary = "添加设备", description = "将设备绑定到当前用户，如果设备不存在则自动创建")
    @PostMapping("/add")
    public ApiResponse<DeviceVO> addDevice(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Valid @RequestBody AddDeviceRequest request) {
        log.info("添加设备 - userId={}, request={}", currentUserId, request);
        // 参数校验
        if (!StringUtils.hasText(request.getDeviceId())) {
            log.warn("添加设备失败，设备ID为空 - userId={}", currentUserId);
            return ApiResponse.error(HTTP_BAD_REQUEST, MSG_DEVICE_ID_REQUIRED);
        }

        DeviceVO device = deviceService.addDevice(currentUserId, request);
        log.info("添加设备成功 - userId={}, deviceId={}", currentUserId, request.getDeviceId());
        return ApiResponse.success(device);
    }

    /**
     * 删除/解绑设备
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @return 空响应
     */
    @Operation(summary = "删除设备", description = "解除当前用户与指定设备的绑定关系")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDevice(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId) {
        log.info("删除设备 - userId={}, deviceId={}", currentUserId, deviceId);
        deviceService.deleteDevice(currentUserId, deviceId);
        log.info("删除设备成功 - userId={}, deviceId={}", currentUserId, deviceId);
        return ApiResponse.success(null);
    }

    /**
     * 更新设备信息
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @param request       更新请求参数
     * @return 更新后的设备信息
     */
    @Operation(summary = "更新设备信息", description = "更新设备的基础信息，目前支持修改设备名称")
    @PutMapping("/{id}")
    public ApiResponse<DeviceVO> updateDevice(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId,
            @Valid @RequestBody UpdateDeviceRequest request) {
        log.info("更新设备 - userId={}, deviceId={}, request={}", currentUserId, deviceId, request);
        DeviceVO device = deviceService.updateDevice(deviceId, request, currentUserId);
        if (device == null) {
            log.warn("更新设备失败，设备不存在 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_NOT_FOUND, MSG_DEVICE_NOT_FOUND);
        }

        log.info("更新设备成功 - userId={}, deviceId={}", currentUserId, deviceId);
        return ApiResponse.success(device);
    }

    /**
     * 上传设备预览图
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @param file          预览图文件
     * @return 上传后的图片URL
     */
    @Operation(summary = "上传设备预览图", description = "上传设备的预览截图，用于在设备列表中展示")
    @PostMapping("/{id}/preview")
    public ApiResponse<Map<String, String>> uploadPreview(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId,
            @RequestParam("file") MultipartFile file) {
        log.info("上传设备预览图 - userId={}, deviceId={}, fileName={}", 
                currentUserId, deviceId, file != null ? file.getOriginalFilename() : null);
        
        // 检查用户是否有该设备的权限
        if (!deviceService.hasUserDevice(currentUserId, deviceId)) {
            log.warn("上传预览图失败，无权限 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_FORBIDDEN, MSG_NO_PERMISSION);
        }
        
        // 检查设备是否存在
        if (!deviceService.deviceExists(deviceId)) {
            log.warn("上传预览图失败，设备不存在 - deviceId={}", deviceId);
            return ApiResponse.error(HTTP_NOT_FOUND, MSG_DEVICE_NOT_FOUND);
        }
        
        // 检查文件
        if (file == null || file.isEmpty()) {
            log.warn("上传预览图失败，文件为空 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_BAD_REQUEST, "文件不能为空");
        }
        
        try {
            // 保存到本地
            String previewUrl = savePreviewFile(deviceId, file);
            if (previewUrl == null) {
                log.error("上传预览图失败 - deviceId={}", deviceId);
                return ApiResponse.error(HTTP_INTERNAL_ERROR, "上传失败");
            }
            
            // 更新设备的预览图URL
            UpdateDeviceRequest updateRequest = new UpdateDeviceRequest();
            updateRequest.setLastPreviewUrl(previewUrl);
            deviceService.updateDevice(deviceId, updateRequest, currentUserId);
            
            log.info("上传设备预览图成功 - userId={}, deviceId={}, url={}", currentUserId, deviceId, previewUrl);
            
            Map<String, String> data = new HashMap<>();
            data.put("url", previewUrl);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("上传预览图异常 - userId={}, deviceId={}", currentUserId, deviceId, e);
            return ApiResponse.error(HTTP_INTERNAL_ERROR, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 保存设备预览图到本地
     *
     * @param deviceId 设备ID
     * @param file     预览图文件
     * @return 相对URL
     */
    private String savePreviewFile(String deviceId, MultipartFile file) throws IOException {
        String baseDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "previews" + File.separator + deviceId;
        File dir = new File(baseDir);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                throw new IOException("无法创建上传目录");
            }
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = "preview_" + System.currentTimeMillis() + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);
        // 返回相对 URL，前端自行拼接域名
        return "/uploads/previews/" + deviceId + "/" + filename;
    }

    /**
     * 获取本地录像列表
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @param date          日期过滤（可选，格式：yyyy-MM-dd）
     * @param page          页码（从1开始，默认1）
     * @param pageSize      每页数量（默认20）
     * @return 分页的录像列表
     */
    @Operation(summary = "获取本地录像列表", description = "获取设备TF卡上的本地录像列表，支持按日期过滤和分页")
    @GetMapping("/{id}/local-videos")
    public ApiResponse<LocalVideoPageVO> listLocalVideos(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId,
            @Parameter(description = "日期过滤，格式：yyyy-MM-dd") @RequestParam(value = "date", required = false) String date,
            @Parameter(description = "页码，从1开始") @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {
        log.info("获取本地录像列表 - userId={}, deviceId={}, date={}, page={}, pageSize={}", 
                currentUserId, deviceId, date, page, pageSize);
        LocalVideoPageVO result = deviceService.listLocalVideos(currentUserId, deviceId, date, page, pageSize);
        if (result == null) {
            log.warn("获取本地录像列表失败，无权限 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_FORBIDDEN, MSG_NO_PERMISSION);
        }
        log.info("获取本地录像列表成功 - userId={}, deviceId={}, total={}", 
                currentUserId, deviceId, result.getTotal());
        return ApiResponse.success(result);
    }

    /**
     * 清除云录像
     * 异步删除设备的所有云存储视频文件，立即返回
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @return 提示信息
     */
    @Operation(summary = "清除云录像", description = "异步删除指定设备的所有云存储视频文件，立即返回")
    @DeleteMapping("/{id}/cloud-videos")
    public ApiResponse<Void> clearCloudVideos(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId) {
        log.info("清除云录像 - userId={}, deviceId={}", currentUserId, deviceId);
        
//        // 检查用户是否有该设备的权限
//        if (!deviceService.hasUserDevice(currentUserId, deviceId)) {
//            log.warn("清除云录像失败，无权限 - userId={}, deviceId={}", currentUserId, deviceId);
//            return ApiResponse.error(HTTP_FORBIDDEN, MSG_NO_PERMISSION);
//        }
//
//        // 检查设备是否存在
//        if (!deviceService.deviceExists(deviceId)) {
//            log.warn("清除云录像失败，设备不存在 - deviceId={}", deviceId);
//            return ApiResponse.error(HTTP_NOT_FOUND, MSG_DEVICE_NOT_FOUND);
//        }
        
        // 异步删除，立即返回
        cloudStorageService.deleteAllDeviceVideosAsync(deviceId);
        log.info("清除云录像任务已提交 - userId={}, deviceId={}", currentUserId, deviceId);
        
        return ApiResponse.success("清除任务已提交，正在后台删除中", null);
    }

    /**
     * 云台PTZ控制
     *
     * @param currentUserId 当前登录用户ID
     * @param deviceId      设备ID
     * @param request       云台控制请求
     * @return 空响应
     */
    @Operation(summary = "云台控制", description = "控制设备云台转动方向，支持上下左右及停止")
    @PostMapping("/{id}/ptz")
    public ApiResponse<Void> ptzControl(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId,
            @Valid @RequestBody PtzControlRequest request) {
        log.info("云台控制 - userId={}, deviceId={}, request={}", currentUserId, deviceId, request);
        // 参数校验
        if (!StringUtils.hasText(request.getDirection())) {
            log.warn("云台控制失败，方向参数为空 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_BAD_REQUEST, MSG_DIRECTION_REQUIRED);
        }

        try {
            Boolean result = deviceService.sendPtzCommand(currentUserId, deviceId, request);
            if (result == null) {
                log.warn("云台控制失败，无权限 - userId={}, deviceId={}", currentUserId, deviceId);
                return ApiResponse.error(HTTP_FORBIDDEN, MSG_NO_PERMISSION);
            }
            log.info("云台控制成功 - userId={}, deviceId={}, direction={}", currentUserId, deviceId, request.getDirection());
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("发送PTZ指令失败 - userId={}, deviceId={}, direction={}", currentUserId, deviceId, request.getDirection(), e);
            return ApiResponse.error(HTTP_INTERNAL_ERROR, "发送PTZ指令失败: " + e.getMessage());
        }
    }

    /**
     * App 直连 MQTT 后，同步设备配置变更到后端数据库（仅记录，不发送MQTT）
     */
    @Operation(summary = "同步设备设置", description = "用于App直连MQTT成功后回写设备配置到后端，不触发MQTT下发")
    @PostMapping("/{id}/settings/sync-direct")
    public ApiResponse<DeviceDetailVO> syncDeviceSettingsFromDirectMqtt(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID") @PathVariable("id") String deviceId,
            @RequestBody DirectMqttSettingsSyncRequest request) {
        log.info("同步设备设置(直连MQTT) - userId={}, deviceId={}, request={}", currentUserId, deviceId, request);

        if (!deviceService.hasUserDevice(currentUserId, deviceId)) {
            log.warn("同步设备设置失败，无权限 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_FORBIDDEN, MSG_NO_PERMISSION);
        }

        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.warn("同步设备设置失败，设备不存在 - userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(HTTP_NOT_FOUND, MSG_DEVICE_NOT_FOUND);
        }

        boolean updated = false;

        if (request.getRotate() != null) {
            if (request.getRotate() != 0 && request.getRotate() != 1) {
                return ApiResponse.error(HTTP_BAD_REQUEST, "rotate 仅支持 0 或 1");
            }
            device.setRotate(request.getRotate());
            updated = true;
        }

        if (request.getWhiteLed() != null) {
            if (request.getWhiteLed() != 0 && request.getWhiteLed() != 1) {
                return ApiResponse.error(HTTP_BAD_REQUEST, "white_led 仅支持 0 或 1");
            }
            device.setWhiteLed(request.getWhiteLed());
            updated = true;
        }

        if (request.getBulbDetect() != null) {
            if (request.getBulbDetect() < 0 || request.getBulbDetect() > 2) {
                return ApiResponse.error(HTTP_BAD_REQUEST, "bulb_detect 仅支持 0~2");
            }
            device.setBulbDetect(request.getBulbDetect());
            updated = true;
        }

        if (request.getBulbBrightness() != null) {
            if (request.getBulbBrightness() < 0 || request.getBulbBrightness() > 100) {
                return ApiResponse.error(HTTP_BAD_REQUEST, "bulb_brightness 仅支持 0~100");
            }
            device.setBulbBrightness(request.getBulbBrightness());
            updated = true;
        }

        if (request.getBulbEnable() != null) {
            if (request.getBulbEnable() != 0 && request.getBulbEnable() != 1) {
                return ApiResponse.error(HTTP_BAD_REQUEST, "bulb_enable 仅支持 0 或 1");
            }
            device.setBulbEnable(request.getBulbEnable());
            updated = true;
        }

        if (request.getBulbTimeOn1() != null) {
            device.setBulbTimeOn1(request.getBulbTimeOn1());
            updated = true;
        }

        if (request.getBulbTimeOff1() != null) {
            device.setBulbTimeOff1(request.getBulbTimeOff1());
            updated = true;
        }

        if (request.getBulbTimeOn2() != null) {
            device.setBulbTimeOn2(request.getBulbTimeOn2());
            updated = true;
        }

        if (request.getBulbTimeOff2() != null) {
            device.setBulbTimeOff2(request.getBulbTimeOff2());
            updated = true;
        }

        if (!updated) {
            return ApiResponse.error(HTTP_BAD_REQUEST, MSG_NO_SETTINGS_PROVIDED);
        }

        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.updateById(device);

        DeviceDetailVO detail = deviceService.getDeviceDetail(currentUserId, deviceId);
        log.info("同步设备设置成功(直连MQTT) - userId={}, deviceId={}", currentUserId, deviceId);
        return ApiResponse.success("设置同步成功", detail);
    }

    @Schema(description = "直连MQTT设备设置同步请求")
    public static class DirectMqttSettingsSyncRequest {
        @Schema(description = "画面旋转: 0-正常, 1-旋转180度")
        private Integer rotate;

        @Schema(description = "白光灯: 0-关闭, 1-开启")
        private Integer whiteLed;

        @Schema(description = "灯泡模式: 0-手动, 1-自动, 2-定时")
        private Integer bulbDetect;

        @Schema(description = "灯泡亮度: 0-100")
        private Integer bulbBrightness;

        @Schema(description = "灯泡开关: 0-关, 1-开")
        private Integer bulbEnable;

        @Schema(description = "定时1开启时间, 格式HH:mm")
        private String bulbTimeOn1;

        @Schema(description = "定时1关闭时间, 格式HH:mm")
        private String bulbTimeOff1;

        @Schema(description = "定时2开启时间, 格式HH:mm")
        private String bulbTimeOn2;

        @Schema(description = "定时2关闭时间, 格式HH:mm")
        private String bulbTimeOff2;

        public Integer getRotate() {
            return rotate;
        }

        public void setRotate(Integer rotate) {
            this.rotate = rotate;
        }

        public Integer getWhiteLed() {
            return whiteLed;
        }

        public void setWhiteLed(Integer whiteLed) {
            this.whiteLed = whiteLed;
        }

        public Integer getBulbDetect() {
            return bulbDetect;
        }

        public void setBulbDetect(Integer bulbDetect) {
            this.bulbDetect = bulbDetect;
        }

        public Integer getBulbBrightness() {
            return bulbBrightness;
        }

        public void setBulbBrightness(Integer bulbBrightness) {
            this.bulbBrightness = bulbBrightness;
        }

        public Integer getBulbEnable() {
            return bulbEnable;
        }

        public void setBulbEnable(Integer bulbEnable) {
            this.bulbEnable = bulbEnable;
        }

        public String getBulbTimeOn1() {
            return bulbTimeOn1;
        }

        public void setBulbTimeOn1(String bulbTimeOn1) {
            this.bulbTimeOn1 = bulbTimeOn1;
        }

        public String getBulbTimeOff1() {
            return bulbTimeOff1;
        }

        public void setBulbTimeOff1(String bulbTimeOff1) {
            this.bulbTimeOff1 = bulbTimeOff1;
        }

        public String getBulbTimeOn2() {
            return bulbTimeOn2;
        }

        public void setBulbTimeOn2(String bulbTimeOn2) {
            this.bulbTimeOn2 = bulbTimeOn2;
        }

        public String getBulbTimeOff2() {
            return bulbTimeOff2;
        }

        public void setBulbTimeOff2(String bulbTimeOff2) {
            this.bulbTimeOff2 = bulbTimeOff2;
        }

        @Override
        public String toString() {
            return "DirectMqttSettingsSyncRequest{" +
                    "rotate=" + rotate +
                    ", whiteLed=" + whiteLed +
                    ", bulbDetect=" + bulbDetect +
                    ", bulbBrightness=" + bulbBrightness +
                    ", bulbEnable=" + bulbEnable +
                    ", bulbTimeOn1='" + bulbTimeOn1 + '\'' +
                    ", bulbTimeOff1='" + bulbTimeOff1 + '\'' +
                    ", bulbTimeOn2='" + bulbTimeOn2 + '\'' +
                    ", bulbTimeOff2='" + bulbTimeOff2 + '\'' +
                    '}';
        }
    }
}
