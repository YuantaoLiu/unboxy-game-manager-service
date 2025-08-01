package com.unboxy.gamemanagerservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TranscribeCreateUpdateMetadata {
    String languageCode;
    String title;
    String description;
    int duration;
    String note;
}
