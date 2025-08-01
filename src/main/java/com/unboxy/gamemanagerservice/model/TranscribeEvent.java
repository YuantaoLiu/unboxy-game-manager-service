package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranscribeEvent {
    String version;
    String id;
    @JsonProperty("detail-type")
    String detailType;
    String source;
    String account;
    String time;
    String region;
    Detail detail;
    List<String> resources;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Detail {
        @JsonProperty("TranscriptionJobName")
        String transcriptionJobName;
        @JsonProperty("TranscriptionJobStatus")
        String transcriptionJobStatus;
        @JsonProperty("FailureReason")
        String failureReason;
    }
}
