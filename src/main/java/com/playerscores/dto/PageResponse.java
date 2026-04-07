package com.playerscores.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long total;
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        PageResponse<T> response = new PageResponse<>();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotal(total);
        response.setTotalPages(size == 0 ? 0 : (int) Math.ceil((double) total / size));
        return response;
    }
}
