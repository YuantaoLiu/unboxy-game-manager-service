package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Item(
        int id,
        String type,
        List<Alternative> alternatives,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime
) {}
