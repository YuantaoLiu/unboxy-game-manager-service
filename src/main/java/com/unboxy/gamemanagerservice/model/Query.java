package com.unboxy.gamemanagerservice.model;

import java.util.List;

public record Query(LogicalOperatorType operator, List<QueryClause> clauses) {}