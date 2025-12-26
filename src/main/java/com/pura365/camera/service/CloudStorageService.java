package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.config.StorageConfig;
import com.pura365.camera.domain.CloudVideo;
import com.pura365.camera.domain.Device;
import com.pura365.camera.model.cloud.ManualUploadResponse;
import com.pura365.camera.repository.CloudVideoRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.qiniu.util.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import com.pura365.camera.model.cloud.VideoUploadNotifyRequest;

import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 云存储服务
 * 根据设备地区选择七牛云（国内）或Vultr（国外）
 */
@Service
public class CloudStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudStorageService.class);

    @Autowired
    private StorageConfig.QiniuConfig qiniuConfig;

    @Autowired
    private StorageConfig.VultrConfig vultrConfig;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private CloudVideoRepository cloudVideoRepository;

    /**
     * 判断设备是否在国内
     */
    private boolean isChina(String region) {
        if (region == null || region.isEmpty()) {
            return true; // 默认国内
        }
        String r = region.toLowerCase();
        return r.equals("cn") || r.equals("china") || r.startsWith("cn-");
    }

    /**
     * 获取设备的云存储配置
     * 返回给摄像头用于上传视频
     */
    public Map<String, Object> getStorageConfig(String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.warn("设备不存在: {}", deviceId);
            return null;
        }

        Map<String, Object> config = new HashMap<>();
        
        if (isChina(device.getRegion())) {
            // 国内：七牛云配置
            config.put("provider", "qiniu");
            config.put("bucket", qiniuConfig.getBucket());
            config.put("region", ""); // 七牛云自动判断区域
            config.put("domain", qiniuConfig.getDomain());
            config.put("upload_domain", qiniuConfig.getUploadDomain() != null ? 
                qiniuConfig.getUploadDomain() : "https://upload.qiniup.com");
        } else {
            // 国外：Vultr S3配置
            config.put("provider", "s3");
            config.put("endpoint", vultrConfig.getEndpoint());
            config.put("bucket", vultrConfig.getBucket());
            config.put("region", vultrConfig.getRegion());
        }

        log.info("获取设备云存储配置 - deviceId: {}, provider: {}", deviceId, config.get("provider"));
        return config;
    }

    /**
     * 生成上传凭证
     * 摄像头调用此接口获取临时上传凭证
     */
    public Map<String, Object> generateUploadCredentials(String deviceId, String fileName) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.warn("设备不存在: {}", deviceId);
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        
        if (isChina(device.getRegion())) {
            // 国内：生成七牛云上传Token
            String key = generateVideoKey(deviceId, fileName);
            Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
            
            // Token有效期1小时
            long expireSeconds = 3600;
            String uploadToken = auth.uploadToken(qiniuConfig.getBucket(), key, expireSeconds, null);
            
            result.put("provider", "qiniu");
            result.put("upload_token", uploadToken);
            result.put("key", key);
            result.put("upload_url", qiniuConfig.getUploadDomain() != null ? 
                qiniuConfig.getUploadDomain() : "https://upload.qiniup.com");
            
            log.info("生成七牛云上传凭证 - deviceId: {}, key: {}", deviceId, key);
        } else {
            // 国外：生成Vultr S3预签名URL
            String key = generateVideoKey(deviceId, fileName);
            
            try {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    vultrConfig.getAccessKey(), 
                    vultrConfig.getSecretKey()
                );
                
                S3Presigner presigner = S3Presigner.builder()
                    .region(Region.of(vultrConfig.getRegion()))
                    .endpointOverride(URI.create(vultrConfig.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
                
                PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(vultrConfig.getBucket())
                    .key(key)
                    .build();
                
                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .putObjectRequest(objectRequest)
                    .build();
                
                PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
                
                result.put("provider", "s3");
                result.put("upload_url", presignedRequest.url().toString());
                result.put("key", key);
                
                presigner.close();
                
                log.info("生成Vultr S3预签名URL - deviceId: {}, key: {}", deviceId, key);
            } catch (Exception e) {
                log.error("生成S3预签名URL失败", e);
                return null;
            }
        }
        
        return result;
    }

    /**
     * 记录视频上传信息
     * 摄像头上传成功后调用此方法记录到数据库
     */
    public boolean recordVideoUpload(String deviceId, VideoUploadNotifyRequest request) {
        try {
            CloudVideo video = new CloudVideo();
            
            // 生成视频ID
            String videoId = "video_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            video.setVideoId(videoId);
            video.setDeviceId(deviceId);
            
            // 从请求中提取信息
            video.setTitle(request.getTitle());
            video.setType(request.getType() != null ? request.getType() : "recording");
            video.setDuration(request.getDuration());
            
            // 构建视频URL
            String key = request.getKey();
            Device device = deviceRepository.selectById(deviceId);
            
            if (device != null && isChina(device.getRegion())) {
                // 七牛云：使用CDN域名
                video.setVideoUrl("https://" + qiniuConfig.getDomain() + "/" + key);
            } else {
                // Vultr：使用S3 URL
                video.setVideoUrl(vultrConfig.getEndpoint() + "/" + vultrConfig.getBucket() + "/" + key);
            }
            
            // 缩略图（如果提供）
            if (request.getThumbnail() != null && !request.getThumbnail().isEmpty()) {
                video.setThumbnail(request.getThumbnail());
            }
            
            video.setCreatedAt(new Date());
            
            cloudVideoRepository.insert(video);
            
            log.info("视频记录已保存 - videoId: {}, deviceId: {}, url: {}", 
                videoId, deviceId, video.getVideoUrl());
            
            return true;
        } catch (Exception e) {
            log.error("保存视频记录失败 - deviceId: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 从云存储查询视频列表
     * 直接从S3查询指定设备的视频文件
     * 
     * @param deviceId 设备ID
     * @param date 日期筛选，格式：YYYY-MM-DD，可为null表示不筛选
     * @return 视频列表
     */
    public List<Map<String, Object>> listVideosFromCloud(String deviceId, String date) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.warn("设备不存在: {}", deviceId);
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> videos = new ArrayList<>();
        
        // 构建前缀：{deviceId}/{date}/
        // 例如：A111-bbc1234/2025-12-15/
        String prefix = deviceId + "/";
        if (date != null && !date.isEmpty()) {
            // date 格式已经是 YYYY-MM-DD，直接拼接
            prefix = prefix + date + "/";
        }
        
        try {
            if (isChina(device.getRegion())) {
                // 国内：使用七牛云S3兼容接口查询
                videos = listVideosFromQiniu(prefix, deviceId);
            } else {
                // 国外：使用Vultr S3查询
                videos = listVideosFromVultr(prefix, deviceId);
            }
        } catch (Exception e) {
            log.error("从云存储查询视频列表失败 - deviceId: {}", deviceId, e);
        }
        
        // 按时间倒序排序
        videos.sort((a, b) -> {
            Date timeA = (Date) a.get("created_at_date");
            Date timeB = (Date) b.get("created_at_date");
            if (timeA == null || timeB == null) return 0;
            return timeB.compareTo(timeA);
        });
        
        return videos;
    }
    
    /**
     * 从七牛云查询视频列表
     */
    private List<Map<String, Object>> listVideosFromQiniu(String prefix, String deviceId) {
        List<Map<String, Object>> videos = new ArrayList<>();
        
        try {
            // 七牛云S3兼容接口
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                qiniuConfig.getAccessKey(),
                qiniuConfig.getSecretKey()
            );
            
            S3Client s3Client = S3Client.builder()
                .region(Region.of("cn-south-1"))
                .endpointOverride(URI.create("https://s3.cn-south-1.qiniucs.com"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
            
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(qiniuConfig.getBucket())
                .prefix(prefix)
                .maxKeys(100)
                .build();
            
            ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);
            
            for (S3Object obj : response.contents()) {
                // 返回所有文件（视频和jpg），在Controller层再过滤
                Map<String, Object> video = parseVideoFromS3Object(obj, deviceId, true);
                if (video != null) {
                    videos.add(video);
                }
            }
            
            s3Client.close();
            
        } catch (Exception e) {
            log.error("从七牛云查询视频失败", e);
        }
        
        return videos;
    }
    
    /**
     * 从Vultr查询视频列表
     */
    private List<Map<String, Object>> listVideosFromVultr(String prefix, String deviceId) {
        List<Map<String, Object>> videos = new ArrayList<>();
        
        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                vultrConfig.getAccessKey(),
                vultrConfig.getSecretKey()
            );
            
            S3Client s3Client = S3Client.builder()
                .region(Region.of(vultrConfig.getRegion()))
                .endpointOverride(URI.create(vultrConfig.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
            
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(vultrConfig.getBucket())
                .prefix(prefix)
                .maxKeys(100)
                .build();
            
            ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);
            
            for (S3Object obj : response.contents()) {
                // 返回所有文件（视频和jpg），在Controller层再过滤
                Map<String, Object> video = parseVideoFromS3Object(obj, deviceId, false);
                if (video != null) {
                    videos.add(video);
                }
            }
            
            s3Client.close();
            
        } catch (Exception e) {
            log.error("从Vultr查询视频失败", e);
        }
        
        return videos;
    }
    
    /**
     * 从S3对象解析视频信息
     */
    private Map<String, Object> parseVideoFromS3Object(S3Object obj, String deviceId, boolean isQiniu) {
        Map<String, Object> video = new HashMap<>();

        String key = obj.key();
        String fileName = key.substring(key.lastIndexOf('/') + 1);

        // 生成视频ID（使用key的hash）
        String videoId = "video_" + Math.abs(key.hashCode());
        video.put("id", videoId);
        video.put("device_id", deviceId);
        video.put("key", key);
        video.put("file_name", fileName);
        video.put("file_size", obj.size());

        // 构建视频URL
        String videoUrl;
        if (isQiniu) {
            videoUrl = buildQiniuSignedDownloadUrl(key);
        } else {
            videoUrl = vultrConfig.getEndpoint() + "/" + vultrConfig.getBucket() + "/" + key;
        }
        video.put("video_url", videoUrl);

        // 从文件名解析时间（假设文件名格式：1702012345678_xxx.mp4）
        Date createdAt = obj.lastModified() != null
            ? Date.from(obj.lastModified())
            : parseTimeFromFileName(fileName);
        video.put("created_at_date", createdAt);

        if (createdAt != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            video.put("created_at", sdf.format(createdAt));
        }

        // 默认值
        video.put("type", "recording");
        video.put("title", "云录像");
        video.put("thumbnail_url", null);
        video.put("duration", null);

        return video;
    }

    /**
     * 七牛云下载链接（私有空间需要带 token；公开空间带了也能用）
     */
    private String buildQiniuSignedDownloadUrl(String key) {
        String domain = qiniuConfig.getDomain();
        if (domain == null || domain.trim().isEmpty()) {
            return key;
        }

        String base;
        String d = domain.trim();
        if (d.startsWith("http://") || d.startsWith("https://")) {
            base = d.replaceAll("/$", "");
        } else {
            base = ("https://" + d).replaceAll("/$", "");
        }

        String publicUrl = base + "/" + key;

        try {
            Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
            // 1小时有效期（秒）
            return auth.privateDownloadUrl(publicUrl, 3600);
        } catch (Exception e) {
            log.warn("生成七牛云下载链接失败，回退为原始URL - key: {}", key, e);
            return publicUrl;
        }
    }
    
    /**
     * 从文件名解析时间戳
     */
    private Date parseTimeFromFileName(String fileName) {
        // 尝试从文件名提取时间戳（假设格式：1702012345678_xxx.mp4）
        Pattern pattern = Pattern.compile("^(\\d{13})_");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            try {
                long timestamp = Long.parseLong(matcher.group(1));
                return new Date(timestamp);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return new Date();
    }
    
    /**
     * 判断是否为视频文件
     */
    private boolean isVideoFile(String key) {
        String lower = key.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".h264") || 
               lower.endsWith(".avi") || lower.endsWith(".mkv") ||
               lower.endsWith(".mov") || lower.endsWith(".ts");
    }
    
    /**
     * 生成视频存储路径key
     * 格式: {deviceId}/{date}/{filename}
     * 例如: A111-bbc1234/2025-12-15/095337M0021.mp4
     */
    private String generateVideoKey(String deviceId, String fileName) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        
        // 如果没有提供文件名，生成一个
        if (fileName == null || fileName.isEmpty()) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            fileName = timestamp + ".mp4";
        }
        
        return String.format("%s/%s/%s", deviceId, date, fileName);
    }

    /**
     * 手动上传对象到云存储（用于测试）
     * - 国内：七牛云 S3 兼容接口
     * - 国外：Vultr S3 兼容接口
     *
     * 注意：不要在返回或日志中输出 accessKey/secretKey。
     */
    public ManualUploadResponse uploadObjectForTest(String deviceId,
                                                   String bucket,
                                                   String key,
                                                   String originalFileName,
                                                   String contentType,
                                                   long size,
                                                   InputStream inputStream) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return null;
        }

        boolean china = isChina(device.getRegion());

        String provider;
        String endpoint;
        String region;
        String accessKey;
        String secretKey;
        String defaultBucket;

        if (china) {
            provider = "qiniu";
            endpoint = "https://s3.cn-south-1.qiniucs.com";
            region = "cn-south-1";
            accessKey = qiniuConfig.getAccessKey();
            secretKey = qiniuConfig.getSecretKey();
            defaultBucket = qiniuConfig.getBucket();
        } else {
            provider = "s3";
            endpoint = vultrConfig.getEndpoint();
            region = vultrConfig.getRegion();
            accessKey = vultrConfig.getAccessKey();
            secretKey = vultrConfig.getSecretKey();
            defaultBucket = vultrConfig.getBucket();
        }

        String bucketToUse = (bucket != null && !bucket.trim().isEmpty()) ? bucket.trim() : defaultBucket;
        String keyToUse = (key != null && !key.trim().isEmpty()) ? key.trim() : generateVideoKey(deviceId, originalFileName);

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Client s3Client = null;

        try {
            s3Client = S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                .bucket(bucketToUse)
                .key(keyToUse);

            if (contentType != null && !contentType.trim().isEmpty()) {
                reqBuilder.contentType(contentType);
            }

            PutObjectResponse putResp = s3Client.putObject(reqBuilder.build(), RequestBody.fromInputStream(inputStream, size));

            ManualUploadResponse resp = new ManualUploadResponse();
            resp.setUploaded(true);
            resp.setProvider(provider);
            resp.setEndpoint(endpoint);
            resp.setRegion(region);
            resp.setBucket(bucketToUse);
            resp.setKey(keyToUse);
            resp.setEtag(putResp != null ? putResp.eTag() : null);
            resp.setContentType(contentType);
            resp.setSize(size);

            // 推断可访问 URL（仅供测试）
            String url = null;
            if (china) {
                if (qiniuConfig.getDomain() != null && !qiniuConfig.getDomain().trim().isEmpty()) {
                    url = "https://" + qiniuConfig.getDomain().trim() + "/" + keyToUse;
                }
            } else {
                if (endpoint != null && !endpoint.trim().isEmpty()) {
                    url = endpoint.replaceAll("/$", "") + "/" + bucketToUse + "/" + keyToUse;
                }
            }
            resp.setUrl(url);

            log.info("手动上传对象成功 - deviceId: {}, provider: {}, bucket: {}, key: {}", deviceId, provider, bucketToUse, keyToUse);
            return resp;
        } catch (Exception e) {
            log.error("手动上传对象失败 - deviceId: {}, bucket: {}, key: {}", deviceId, bucketToUse, keyToUse, e);
            ManualUploadResponse resp = new ManualUploadResponse();
            resp.setUploaded(false);
            resp.setProvider(provider);
            resp.setEndpoint(endpoint);
            resp.setRegion(region);
            resp.setBucket(bucketToUse);
            resp.setKey(keyToUse);
            resp.setContentType(contentType);
            resp.setSize(size);
            return resp;
        } finally {
            if (s3Client != null) {
                try {
                    s3Client.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
