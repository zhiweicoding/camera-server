package com.pura365.camera.controller.device;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.device.*;
import com.pura365.camera.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
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

    @Autowired
    private DeviceService deviceService;
    
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
        DeviceVO device = deviceService.updateDevice(deviceId, request);
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
            deviceService.updateDevice(deviceId, updateRequest);
            
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
}
