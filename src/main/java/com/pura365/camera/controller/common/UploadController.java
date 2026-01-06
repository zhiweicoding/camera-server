package com.pura365.camera.controller.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用文件上传接口
 */
@Tag(name = "文件上传", description = "通用文件上传接口")
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    /**
     * 允许上传的图片格式
     */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    /**
     * 最大文件大小：10MB
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 上传图片
     */
    @Operation(summary = "上传图片", description = "上传图片文件，支持 jpg/png/gif/webp/bmp 格式，最大10MB")
    @PostMapping("/image")
    public ApiResponse<Map<String, Object>> uploadImage(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam("file") MultipartFile file) {
        
        log.info("上传图片 - userId={}, fileName={}, size={}", 
                currentUserId, 
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : 0);

        // 验证登录状态
        if (currentUserId == null) {
            return ApiResponse.error(401, "未登录");
        }

        // 验证文件
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }

        // 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小不能超过10MB");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            return ApiResponse.error(400, "不支持的图片格式，仅支持 jpg/png/gif/webp/bmp");
        }

        try {
            String imageUrl = saveImageFile(file, "images");
            log.info("上传图片成功 - userId={}, url={}", currentUserId, imageUrl);
            
            Map<String, Object> data = new HashMap<>();
            data.put("url", imageUrl);
            return ApiResponse.success(data);
        } catch (IOException e) {
            log.error("上传图片异常 - userId={}", currentUserId, e);
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传营业执照
     */
    @Operation(summary = "上传营业执照", description = "上传营业执照图片")
    @PostMapping("/license")
    public ApiResponse<Map<String, Object>> uploadLicense(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam("file") MultipartFile file) {
        
        log.info("上传营业执照 - userId={}, fileName={}", currentUserId, 
                file != null ? file.getOriginalFilename() : null);

        if (currentUserId == null) {
            return ApiResponse.error(401, "未登录");
        }

        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小不能超过10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            return ApiResponse.error(400, "不支持的图片格式");
        }

        try {
            String imageUrl = saveImageFile(file, "licenses");
            log.info("上传营业执照成功 - userId={}, url={}", currentUserId, imageUrl);
            
            Map<String, Object> data = new HashMap<>();
            data.put("url", imageUrl);
            return ApiResponse.success(data);
        } catch (IOException e) {
            log.error("上传营业执照异常 - userId={}", currentUserId, e);
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 保存图片文件
     * @param file 上传的文件
     * @param subDir 子目录名称
     * @return 图片访问URL
     */
    private String saveImageFile(MultipartFile file, String subDir) throws IOException {
        String baseDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + subDir;
        File dir = new File(baseDir);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                throw new IOException("无法创建上传目录");
            }
        }

        String original = file.getOriginalFilename();
        String ext = ".jpg"; // 默认扩展名
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);

        // 返回相对 URL
        return "/uploads/" + subDir + "/" + filename;
    }
}
