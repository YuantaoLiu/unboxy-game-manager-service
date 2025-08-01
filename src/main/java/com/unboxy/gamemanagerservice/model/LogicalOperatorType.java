package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LogicalOperatorType {

    AND("AND"),

    OR("OR");

    private String value;

    LogicalOperatorType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static LogicalOperatorType fromValue(String value) {
        for (LogicalOperatorType b : LogicalOperatorType.values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
}
