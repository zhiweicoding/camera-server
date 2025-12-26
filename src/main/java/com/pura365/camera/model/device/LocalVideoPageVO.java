package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 本地录像分页响应
 *
 * @author camera-server
 */
@Schema(description = "本地录像分页信息")
public class LocalVideoPageVO {

    /**
     * 视频列表
     */
    @JsonProperty("list")
    @Schema(description = "视频列表")
    private List<LocalVideoVO> list;

    /**
     * 总数量
     */
    @JsonProperty("total")
    @Schema(description = "总数量")
    private Integer total;

    /**
     * 当前页码
     */
    @JsonProperty("page")
    @Schema(description = "当前页码")
    private Integer page;

    /**
     * 每页数量
     */
    @JsonProperty("page_size")
    @Schema(description = "每页数量")
    private Integer pageSize;

    // Getters and Setters

    public List<LocalVideoVO> getList() {
        return list;
    }

    public void setList(List<LocalVideoVO> list) {
        this.list = list;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
