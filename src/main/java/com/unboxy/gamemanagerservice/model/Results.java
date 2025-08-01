package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Results(
        List<Transcript> transcripts,
        List<Item> items,
        @JsonProperty("audio_segments") List<AudioSegment> audioSegments
) {}
