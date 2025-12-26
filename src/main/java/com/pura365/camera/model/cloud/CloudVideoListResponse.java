package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 云存储视频列表响应
 */
@Data
@Schema(description = "云存储视频列表响应")
public class CloudVideoListResponse {

    /**
     * 视频列表
     */
    @JsonProperty("list")
    @Schema(description = "视频列表")
    private List<CloudVideoVO> list;

    /**
     * 总记录数
     */
    @JsonProperty("total")
    @Schema(description = "总记录数", example = "100")
    private Integer total;

    /**
     * 当前页码
     */
    @JsonProperty("page")
    @Schema(description = "当前页码", example = "1")
    private Integer page;

    /**
     * 每页大小
     */
    @JsonProperty("page_size")
    @Schema(description = "每页大小", example = "20")
    private Integer pageSize;
}
