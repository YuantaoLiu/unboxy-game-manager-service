package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AudioSegment(
        int id,
        String transcript,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        List<Integer> items
) {}
