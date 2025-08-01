package com.unboxy.gamemanagerservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "games")
@Data
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameGenerationMetadata {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String gameStatus;

    @Field(type = FieldType.Keyword)
    private String gameType;

    @Field(type = FieldType.Text)
    private String s3GameUrl;

    @Field(type = FieldType.Text)
    private String publicGameUrl;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Date)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime updatedAt;

    @Field(type = FieldType.Text)
    private String tags;

    @Field(type = FieldType.Text)
    private String generatedPrompt;

    @Field(type = FieldType.Text)
    private String posterUrl;

    @Field(type = FieldType.Text)
    private String aiResponse;
}