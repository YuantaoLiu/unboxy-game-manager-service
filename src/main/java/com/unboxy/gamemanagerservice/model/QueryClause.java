package com.unboxy.gamemanagerservice.model;

public record QueryClause(String lhs, SearchOperatorType operator, String rhs) {
}
