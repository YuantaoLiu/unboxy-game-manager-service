package com.unboxy.gamemanagerservice.model;

public record Pagination(Integer pageNumber, Integer totalPages, Integer pageSize, Long totalSize) {
}
