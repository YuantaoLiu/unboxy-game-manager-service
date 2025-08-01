package com.unboxy.gamemanagerservice.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode
@NoArgsConstructor
public class SearchCriteria {
    String searchText;
    Integer pageNumber;
    Integer pageSize;
    LogicalOperatorType queryOperator;
    List<Query> queries;
}
