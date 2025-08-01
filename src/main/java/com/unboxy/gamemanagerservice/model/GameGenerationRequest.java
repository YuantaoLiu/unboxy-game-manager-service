package com.unboxy.gamemanagerservice.model;

import lombok.Data;

@Data
public class GameGenerationRequest {
    private String id; // Optional: if provided, use this ID instead of generating
    private String title;
    private String description;
    private String gameType; // "platformer", "puzzle", "arcade", etc.
    private String tags;
}