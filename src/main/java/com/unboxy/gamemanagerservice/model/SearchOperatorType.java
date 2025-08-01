package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SearchOperatorType {

    EQ("EQ"),

    NOTEQ("NOTEQ"),

    EQALL("EQALL"),

    IN("IN"),

    GT("GT"),

    GTE("GTE"),

    LT("LT"),

    LTE("LTE"),

    EQIGNORECASE("EQIGNORECASE");

    private String value;

    SearchOperatorType(String value) {
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
    public static SearchOperatorType fromValue(String value) {
        for (SearchOperatorType b : SearchOperatorType.values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
}