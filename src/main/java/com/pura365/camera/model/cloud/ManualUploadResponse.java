package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手动上传视频到云存储响应
 */
@Data
@Schema(description = "手动上传视频到云存储响应")
public class ManualUploadResponse {

    @JsonProperty("uploaded")
    @Schema(description = "是否上传成功", example = "true")
    private Boolean uploaded;

    @JsonProperty("provider")
    @Schema(description = "存储提供商", example = "qiniu")
    private String provider;

    @JsonProperty("endpoint")
    @Schema(description = "S3 Endpoint（不包含密钥）", example = "https://s3.cn-south-1.qiniucs.com")
    private String endpoint;

    @JsonProperty("region")
    @Schema(description = "Region", example = "cn-south-1")
    private String region;

    @JsonProperty("bucket")
    @Schema(description = "Bucket", example = "pura365-cloud-storage")
    private String bucket;

    @JsonProperty("key")
    @Schema(description = "对象Key", example = "videos/A111-bbc1234/20251214/1700000000000_test.mp4")
    private String key;

    @JsonProperty("etag")
    @Schema(description = "ETag", example = "\"d41d8cd98f00b204e9800998ecf8427e\"")
    private String etag;

    @JsonProperty("content_type")
    @Schema(description = "Content-Type", example = "video/mp4")
    private String contentType;

    @JsonProperty("size")
    @Schema(description = "文件大小(字节)", example = "123456")
    private Long size;

    @JsonProperty("url")
    @Schema(description = "可访问URL（如果能推断）")
    private String url;
}
