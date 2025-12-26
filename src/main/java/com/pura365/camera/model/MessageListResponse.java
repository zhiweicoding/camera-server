package com.pura365.camera.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 消息列表分页响应
 */
public class MessageListResponse {

    private List<MessageVO> list;

    private int total;

    private int page;

    @JsonProperty("page_size")
    private int pageSize;

    public MessageListResponse() {
    }

    public MessageListResponse(List<MessageVO> list, int total, int page, int pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<MessageVO> getList() {
        return list;
    }

    public void setList(List<MessageVO> list) {
        this.list = list;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
