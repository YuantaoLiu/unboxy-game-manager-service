package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TranscribeCommonMetadata extends TranscribeCreateUpdateMetadata {
    String id;
    String jobName;
    String transcript;
    String summary;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    OffsetDateTime creationDateTime;
    UserInfo creationUserInfo;
    String contentFileName;
    String transcribeStatus;
    List<AudioSegment> audioSegments;
}
