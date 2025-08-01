package com.unboxy.gamemanagerservice.model;

import java.util.List;

public record SearchResult<T>(String searchText, List<T> items, Pagination pagination) {
}
